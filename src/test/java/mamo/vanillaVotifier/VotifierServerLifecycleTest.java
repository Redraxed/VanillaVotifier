package mamo.vanillaVotifier;

import mamo.vanillaVotifier.event.Event;
import mamo.vanillaVotifier.event.ServerStoppedEvent;
import mamo.vanillaVotifier.utils.RsaUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VotifierServerLifecycleTest {
	@Test
	public void servesOtherClientsWhileOneClientStallsAndStopsCleanly() throws Exception {
		final CountDownLatch stopped = new CountDownLatch(1);
		int port;
		try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = reservation.getLocalPort();
		}

		VanillaVotifier app = new VanillaVotifier(
				new LanguagePack("mamo/vanillaVotifier/lang", "lang"), new StringWriter(), new java.io.File("unused-test-config.yaml"));
		app.getConfig().setInetSocketAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
		app.getConfig().setKeyPair(RsaUtils.genKeyPair(1024));
		app.getConfig().setLogFile(null);
		app.getServer().getListeners().add(new Listener() {
			@Override
			public void onEvent(Event event) {
				if (event instanceof ServerStoppedEvent) {
					stopped.countDown();
				}
			}
		});

		Socket stalledClient = null;
		Socket secondClient = null;
		Socket thirdClient = null;
		try {
			app.getServer().start();
			stalledClient = connectAndReadGreeting(port);
			secondClient = connectAndReadGreeting(port);
			thirdClient = connectAndReadGreeting(port);
			secondClient.close();
			secondClient = null;
			thirdClient.close();
			thirdClient = null;
			app.getServer().stop();
			assertTrue("Server did not close a stalled client during shutdown", stopped.await(3, TimeUnit.SECONDS));
		} finally {
			if (stalledClient != null) stalledClient.close();
			if (secondClient != null) secondClient.close();
			if (thirdClient != null) thirdClient.close();
			if (app.getServer().isRunning()) app.getServer().stop();
		}
	}

	private static Socket connectAndReadGreeting(int port) throws Exception {
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000);
		socket.setSoTimeout(2000);
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		assertEquals("VOTIFIER 1", reader.readLine());
		return socket;
	}
}
