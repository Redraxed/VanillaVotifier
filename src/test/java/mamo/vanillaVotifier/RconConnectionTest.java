package mamo.vanillaVotifier;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class RconConnectionTest {
	@Test
	public void readsFragmentedResponseHeaderAndBody() throws Exception {
		byte[] body = responseBody("accepted");
		byte[] frame = responseFrame(body.length, body);
		assertEquals("accepted", exchange(frame));
	}

	@Test
	public void rejectsTooSmallResponseLength() throws Exception {
		assertInvalidLength(1);
	}

	@Test
	public void rejectsOversizedResponseLength() throws Exception {
		assertInvalidLength(1024 * 1024 + 1);
	}

	@Test
	public void packetLengthUsesUtf8ByteCount() {
		VotifierPacket packet = new VotifierPacket(1, VotifierPacket.Type.COMMAND, "vote-✓");
		assertEquals(10 + "vote-✓".getBytes(StandardCharsets.UTF_8).length, packet.getLength());
	}

	private static void assertInvalidLength(int length) throws Exception {
		byte[] header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(length).array();
		try {
			exchange(header);
			throw new AssertionError("Expected invalid RCON response length to fail");
		} catch (IOException expected) {
			// The client rejects the length before allocating or reading a body.
		}
	}

	private static String exchange(final byte[] frame) throws Exception {
		final ServerSocket server = new ServerSocket(0);
		final AtomicReference<IOException> serverFailure = new AtomicReference<IOException>();
		Thread responder = new Thread(new Runnable() {
			@Override
			public void run() {
				try (Socket client = server.accept()) {
					OutputStream output = client.getOutputStream();
					for (byte value : frame) {
						output.write(value);
						output.flush();
					}
				} catch (IOException e) {
					serverFailure.set(e);
				}
			}
		}, "rcon-test-responder");
		responder.start();
		try (ServerSocket serverSocket = server;
			 RconConnection connection = new RconConnection(new InetSocketAddress("127.0.0.1", server.getLocalPort()), "test")) {
			VotifierPacket response = connection.sendCommand("test");
			responder.join(2000);
			if (responder.isAlive()) {
				throw new AssertionError("RCON test responder did not finish");
			}
			if (serverFailure.get() != null) {
				throw serverFailure.get();
			}
			return response.getPayload();
		}
	}

	private static byte[] responseBody(String payload) {
		byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
		ByteBuffer body = ByteBuffer.allocate(10 + payloadBytes.length).order(ByteOrder.LITTLE_ENDIAN);
		body.putInt(1234);
		body.putInt(VotifierPacket.Type.COMMAND_RESPONSE.toInt());
		body.put(payloadBytes);
		body.put((byte) 0);
		body.put((byte) 0);
		return body.array();
	}

	private static byte[] responseFrame(int length, byte[] body) {
		ByteBuffer frame = ByteBuffer.allocate(4 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(length);
		frame.put(body);
		return frame.array();
	}
}
