/*
 * Copyright (C) 2016  Matteo Morena
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mamo.vanillaVotifier;

import mamo.vanillaVotifier.exception.BrokenPipeException;
import mamo.vanillaVotifier.exception.InvalidRconPasswordException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;

public class RconConnection {
	private static final int SOCKET_TIMEOUT_MILLIS = 5000;
	private static final int MIN_PACKET_LENGTH = 10;
	private static final int MAX_PACKET_LENGTH = 1024 * 1024;
	@NotNull protected InetSocketAddress inetSocketAddress;
	@NotNull protected String password;
	protected int requestId;
	@Nullable protected Socket socket;

	public RconConnection(@NotNull InetSocketAddress inetSocketAddress, @NotNull String password) {
		this.inetSocketAddress = inetSocketAddress;
		this.password = password;
		SecureRandom random = new SecureRandom();
		while (true) {
			requestId = random.nextInt();
			if (requestId != -1) {
				break;
			}
		}
	}

	@NotNull
	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}

	@NotNull
	public String getPassword() {
		return password;
	}

	public int getRequestId() {
		return requestId;
	}

	public synchronized VotifierPacket logIn() throws IOException, InvalidRconPasswordException {
		return sendRequest(new VotifierPacket(getRequestId(), VotifierPacket.Type.LOG_IN, getPassword()));
	}

	@NotNull
	public synchronized VotifierPacket sendCommand(@NotNull String command) throws IOException {
		try {
			return sendRequest(new VotifierPacket(getRequestId(), VotifierPacket.Type.COMMAND, command));
		} catch (InvalidRconPasswordException e) {
			// Can't happen.
		}
		return null;
	}

	@NotNull
	public synchronized VotifierPacket sendRequest(@NotNull VotifierPacket request) throws IOException, InvalidRconPasswordException {
		if (socket == null) {
			socket = new Socket(inetSocketAddress.getAddress(), inetSocketAddress.getPort());
			socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
		}
		byte[] requestBytes = new byte[request.getLength() + Integer.SIZE / 8];
		ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
		requestBuffer.order(ByteOrder.LITTLE_ENDIAN);
		requestBuffer.putInt(request.getLength());
		requestBuffer.putInt(getRequestId());
		requestBuffer.putInt(request.getType().toInt());
			requestBuffer.put(request.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		requestBuffer.put((byte) 0);
		requestBuffer.put((byte) 0);
		try {
			socket.getOutputStream().write(requestBytes);
			socket.getOutputStream().flush();
		} catch (SocketException e) {
			if (e.getMessage().toLowerCase().contains("broken pipe")) {
				throw new BrokenPipeException();
			} else {
				throw e;
			}
		}
		byte[] responseBytes = new byte[Integer.SIZE / 8];
		readFully(socket.getInputStream(), responseBytes);
		ByteBuffer responseBuffer = ByteBuffer.wrap(responseBytes);
		responseBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int responseLength = responseBuffer.getInt();
		if (responseLength < MIN_PACKET_LENGTH || responseLength > MAX_PACKET_LENGTH) {
			throw new IOException("Invalid RCON response length: " + responseLength);
		}
		responseBytes = new byte[responseLength];
		readFully(socket.getInputStream(), responseBytes);
		responseBuffer = ByteBuffer.wrap(responseBytes);
		responseBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int responseRequestId = responseBuffer.getInt();
		VotifierPacket.Type responseType = VotifierPacket.Type.fromInt(responseBuffer.getInt());
		byte[] responsePayload = new byte[responseLength - Integer.SIZE / 8 - Integer.SIZE / 8 - Byte.SIZE / 8 * 2];
		responseBuffer.get(responsePayload);
		responseBuffer.get();
		responseBuffer.get();
		if (request.getType() == VotifierPacket.Type.LOG_IN) {
			if (responseRequestId != getRequestId()) {
				throw new InvalidRconPasswordException();
			}
		}
		return new VotifierPacket(responseLength, responseRequestId, responseType, new String(responsePayload, java.nio.charset.StandardCharsets.UTF_8));
	}

	private static void readFully(java.io.InputStream input, byte[] buffer) throws IOException {
		int offset = 0;
		while (offset < buffer.length) {
			int count = input.read(buffer, offset, buffer.length - offset);
			if (count < 0) {
				throw new BrokenPipeException();
			}
			offset += count;
		}
	}
}
