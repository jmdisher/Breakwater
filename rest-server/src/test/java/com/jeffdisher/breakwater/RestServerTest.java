package com.jeffdisher.breakwater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringRequestContent;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.breakwater.utilities.RestHelpers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;


public class RestServerTest {
	@Test
	public void testBasicHandle() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("TESTING");
				stopLatch.countDown();
			}});
		server.start();
		byte[] data = RestHelpers.get("http://localhost:8080/test1");
		Assert.assertArrayEquals("TESTING".getBytes(StandardCharsets.UTF_8), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testNotFound() throws Throwable {
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("TESTING");
			}});
		server.start();
		byte[] data = RestHelpers.get("http://localhost:8080/test2");
		Assert.assertNull(data);
		server.stop();
	}

	@Test
	public void testDynamicHandle() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("TESTING");
				server.addGetHandler("/test2", 1, new IGetHandler() {
					@Override
					public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
						response.setContentType("text/plain;charset=utf-8");
						response.setStatus(HttpServletResponse.SC_OK);
						response.getWriter().print(variables[0]);
						stopLatch.countDown();
					}});
				stopLatch.countDown();
			}});
		server.start();
		byte[] data = RestHelpers.get("http://localhost:8080/test1");
		Assert.assertArrayEquals("TESTING".getBytes(StandardCharsets.UTF_8), data);
		byte[] data2 = RestHelpers.get("http://localhost:8080/test2/TEST");
		Assert.assertArrayEquals("TEST".getBytes(StandardCharsets.UTF_8), data2);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPutBinary() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addPutHandler("/test", 0, new IPutHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables, InputStream inputStream) throws IOException {
				response.setContentType("application/octet-stream");
				response.setStatus(HttpServletResponse.SC_OK);
				OutputStream stream = response.getOutputStream();
				byte[] buffer = new byte[2];
				int didRead = inputStream.read(buffer);
				while (-1 != didRead) {
					stream.write(buffer, 0, didRead);
					didRead = inputStream.read(buffer);
				}
				stopLatch.countDown();
			}});
		server.start();
		byte[] buffer = new byte[] {1,2,3,4,5};
		byte[] data = RestHelpers.put("http://localhost:8080/test", buffer);
		Assert.assertArrayEquals(buffer, data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPostParts() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addPostMultiPartHandler("/test", 0, new IPostMultiPartHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<byte[]> multiPart) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + multiPart.valueCount());
				stopLatch.countDown();
			}});
		server.start();
		
		StringMultiMap<byte[]> postParts = new StringMultiMap<>();
		postParts.append("var1", "val1".getBytes(StandardCharsets.UTF_8));
		postParts.append("var2", "".getBytes(StandardCharsets.UTF_8));
		byte[] data = RestHelpers.postParts("http://localhost:8080/test", postParts);
		// We expect it to write "2", since there are 2 top-level keys.
		Assert.assertArrayEquals("2".getBytes(), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testDelete() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addDeleteHandler("/test", 0, new IDeleteHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("DELETE/test");
				stopLatch.countDown();
			}});
		server.start();
		
		byte[] data = RestHelpers.delete("http://localhost:8080/test");
		Assert.assertArrayEquals("DELETE/test".getBytes(), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPostPartsDuplicate() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addPostMultiPartHandler("/test", 0, new IPostMultiPartHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<byte[]> multiPart) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + multiPart.valueCount());
				stopLatch.countDown();
			}});
		server.start();
		
		StringMultiMap<byte[]> postParts = new StringMultiMap<>();
		postParts.append("var1", "val1".getBytes(StandardCharsets.UTF_8));
		postParts.append("var1", "a".getBytes(StandardCharsets.UTF_8));
		postParts.append("var2", "b".getBytes(StandardCharsets.UTF_8));
		byte[] data = RestHelpers.postParts("http://localhost:8080/test", postParts);
		// We expect it to write "3", since there are 3 elements.
		Assert.assertArrayEquals("3".getBytes(), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPostFormDuplicate() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addPostFormHandler("/test", 0, new IPostFormHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + formVariables.valueCount());
				stopLatch.countDown();
			}});
		server.start();
		
		StringMultiMap<String> form = new StringMultiMap<>();
		form.append("var1", "val1");
		form.append("var1", "a");
		form.append("var2", "b");
		byte[] data = RestHelpers.postForm("http://localhost:8080/test", form);
		// We expect it to write "3", since there are 3 elements.
		Assert.assertArrayEquals("3".getBytes(), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPostRawBinary() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		server.addPostRawHandler("/test", 0, new IPostRawHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException {
				InputStream stream = request.getInputStream();
				int postSize = 0;
				byte[] raw = new byte[1024];
				while (null != raw)
				{
					int size = stream.read(raw, 0, raw.length);
					if (size > 0)
					{
						postSize += size;
					}
					else
					{
						raw = null;
					}
				}
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + postSize);
				stopLatch.countDown();
			}});
		server.start();
		
		byte[] raw = new byte[] { 1,2,3 };
		byte[] data = RestHelpers.postBinary("http://localhost:8080/test", raw);
		// We expect it to write "3", since there are 3 bytes.
		Assert.assertArrayEquals("3".getBytes(), data);
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testSessionState() throws Throwable {
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		HttpClient httpClient = new HttpClient();
		server.addPostRawHandler("/start", 0, new IPostRawHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				byte[] rawPost = request.getInputStream().readAllBytes();
				if (rawPost.length > 0) {
					response.getWriter().print(new String(rawPost, StandardCharsets.UTF_8));
					request.getSession(true).setAttribute("NAME", new String(rawPost, StandardCharsets.UTF_8));
				} else {
					request.getSession(true).invalidate();
				}
			}});
		server.addGetHandler("/get", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException {
				HttpSession session = request.getSession(false);
				if (null != session) {
					response.setContentType("text/plain;charset=utf-8");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(session.getAttribute("NAME"));
				} else {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
		});
		server.addPutHandler("/put", 0, new IPutHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, InputStream inputStream) throws IOException {
				HttpSession session = request.getSession(false);
				if (null != session) {
					response.setContentType("text/plain;charset=utf-8");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(session.getAttribute("NAME"));
				} else {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
		});
		server.addDeleteHandler("/delete", 0, new IDeleteHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException {
				HttpSession session = request.getSession(false);
				if (null != session) {
					response.setContentType("text/plain;charset=utf-8");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(session.getAttribute("NAME"));
				} else {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
		});
		
		server.start();
		httpClient.start();
		
		String loggedInUserName = "USER_NAME";
		String content = _sendRequest(httpClient, HttpMethod.POST, "http://localhost:8080/start", loggedInUserName);
		Assert.assertEquals(loggedInUserName, content);
		
		content = _sendRequest(httpClient, HttpMethod.GET, "http://localhost:8080/get", loggedInUserName);
		Assert.assertEquals(loggedInUserName, content);
		
		content = _sendRequest(httpClient, HttpMethod.PUT, "http://localhost:8080/put", loggedInUserName);
		Assert.assertEquals(loggedInUserName, content);
		
		content = _sendRequest(httpClient, HttpMethod.DELETE, "http://localhost:8080/delete", loggedInUserName);
		Assert.assertEquals(loggedInUserName, content);
		
		content = _sendRequest(httpClient, HttpMethod.POST, "http://localhost:8080/start", "");
		Assert.assertEquals("", content);
		
		content = _sendRequest(httpClient, HttpMethod.GET, "http://localhost:8080/get", loggedInUserName);
		Assert.assertEquals("", content);

		httpClient.stop();
		server.stop();
	}

	@Test
	public void testStaticContent() throws Throwable {
		TemporaryFolder folder = new TemporaryFolder();
		folder.create();
		File dir = folder.newFolder();
		new File(dir, "temp.txt").createNewFile();
		RestServer server = new RestServer(new InetSocketAddress(8080), new PathResource(dir));
		server.start();
		byte[] found = RestHelpers.get("http://localhost:8080/temp.txt");
		byte[] missing = RestHelpers.get("http://localhost:8080/temp2.txt");
		Assert.assertArrayEquals(new byte[0], found);
		Assert.assertArrayEquals(null, missing);
		server.stop();
	}

	@Test
	public void testWebSocket() throws Throwable {
		CountDownLatch closeLatch = new CountDownLatch(2);
		CountDownLatch clientReadLatch = new CountDownLatch(2);
		Throwable[] failureReference = new Throwable[1];
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		// We want to create a server with 2 protocols, at the same root address, so make sure that we can resolve multiple protocols.
		server.addWebSocketFactory("/ws", 1, "textPrefix", (JettyServerUpgradeRequest upgradeRequest, String[] variables) -> new NamedListener(variables) {
			@Override
			public void onConnect(String name, RemoteEndpoint endpoint)
			{
			}
			@Override
			public void onError(String name, RemoteEndpoint endpoint, Throwable error)
			{
				failureReference[0] = error;
			}
			@Override
			public void onBinary(String name, RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.fail();
			}
			@Override
			public void onText(String name, RemoteEndpoint endpoint, String message)
			{
				try
				{
					endpoint.sendString(name + message);
				}
				catch (IOException e)
				{
					failureReference[0] = e;
				}
			}
			@Override
			public void onClose()
			{
				closeLatch.countDown();
			}
		});
		server.addWebSocketFactory("/ws", 1, "binaryRotate", (JettyServerUpgradeRequest upgradeRequest, String[] variables) -> new NamedListener(variables) {
			@Override
			public void onConnect(String name, RemoteEndpoint endpoint)
			{
			}
			@Override
			public void onError(String name, RemoteEndpoint endpoint, Throwable error)
			{
				failureReference[0] = error;
			}
			@Override
			public void onBinary(String name, RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				byte rotate = Byte.valueOf(name);
				for (int i = 0; i < len; ++i)
				{
					payload[offset + i] = (byte) (payload[offset + i] + rotate);
				}
				try
				{
					endpoint.sendBytes(ByteBuffer.wrap(payload, offset, len));
				}
				catch (IOException e)
				{
					failureReference[0] = e;
				}
			}
			@Override
			public void onText(String name, RemoteEndpoint endpoint, String message)
			{
				Assert.fail();
			}
			@Override
			public void onClose()
			{
				closeLatch.countDown();
			}
		});
		server.start();
		
		String textOut[] = new String[1];
		ProtocolClient textClient = new ProtocolClient("ws://localhost:8080/ws/prefix_", "textPrefix") {
			@Override
			public void onError(RemoteEndpoint endpoint, Throwable error)
			{
				failureReference[0] = error;
			}
			@Override
			public void onBinary(RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.fail();
			}
			@Override
			public void onText(RemoteEndpoint endpoint, String message)
			{
				textOut[0] = message;
				clientReadLatch.countDown();
			}
		};
		byte[] binaryOut = new byte[4];
		ProtocolClient binaryClient = new ProtocolClient("ws://localhost:8080/ws/16", "binaryRotate") {
			@Override
			public void onError(RemoteEndpoint endpoint, Throwable error)
			{
				failureReference[0] = error;
			}
			@Override
			public void onBinary(RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.assertEquals(binaryOut.length, len);
				System.arraycopy(payload, offset, binaryOut, 0, len);
				clientReadLatch.countDown();
			}
			@Override
			public void onText(RemoteEndpoint endpoint, String message)
			{
				Assert.fail();
			}
		};
		textClient.waitForConnect();
		binaryClient.waitForConnect();
		String text = "testText";
		byte[] binary = new byte[] { 0, 1, 2, 3 };
		textClient.sendText(text);
		binaryClient.sendBinary(binary);
		// We want to wait for the clients to finish their round-trip before we stop them.
		clientReadLatch.await();
		textClient.stop();
		binaryClient.stop();
		closeLatch.await();
		server.stop();
		
		Assert.assertNull(failureReference[0]);
		Assert.assertEquals("prefix_testText", textOut[0]);
		Assert.assertArrayEquals(new byte[] { 16, 17, 18, 19 }, binaryOut);
	}

	@Test
	public void testSingleInterface() throws Throwable {
		// Create the static resource.
		TemporaryFolder folder = new TemporaryFolder();
		folder.create();
		File dir = folder.newFolder();
		new File(dir, "temp.txt").createNewFile();
		
		// Find the interfaces.
		List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		// (we can only run the test if there is more than one interface).
		Assume.assumeTrue(interfaces.size() > 1);
		List<InetSocketAddress> addresses = new ArrayList<>();
		for (NetworkInterface network : interfaces)
		{
			for (InetAddress address : Collections.list(network.getInetAddresses()))
			{
				addresses.add(new InetSocketAddress(address, 8080));
			}
		}
		for (InetSocketAddress hostAddress : addresses)
		{
			System.out.println("Host as: " + hostAddress);
			RestServer server = new RestServer(hostAddress, new PathResource(dir));
			server.start();
			for (InetSocketAddress fetchAddress : addresses)
			{
				String hostPart = _addressAsUrlString(fetchAddress.getAddress());
				String url = "http://" + hostPart + ":" + fetchAddress.getPort() + "/temp.txt";
				System.out.print("\tTest as: " + url);
				boolean expectedSuccess = (fetchAddress == hostAddress);
				try
				{
					byte[] found = RestHelpers.get(url);
					byte[] expected = new byte[0];
					Assert.assertArrayEquals(expected, found);
					Assert.assertTrue(expectedSuccess);
					System.out.println(" -- SUCCESS");
				}
				catch (ConnectException e)
				{
					// We failed to connect.
					Assert.assertFalse(expectedSuccess);
					System.out.println(" -- FAILED (expected)");
				}
			}
			server.stop();
		}
	}

	@Test
	public void testSingleInterfaceRandomPort() throws Throwable {
		// Create the static resource.
		TemporaryFolder folder = new TemporaryFolder();
		folder.create();
		File dir = folder.newFolder();
		new File(dir, "temp.txt").createNewFile();
		
		// Find the interfaces.
		List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		// (we can only run the test if there is more than one interface).
		Assume.assumeTrue(interfaces.size() > 1);
		List<InetSocketAddress> addresses = new ArrayList<>();
		for (NetworkInterface network : interfaces)
		{
			for (InetAddress address : Collections.list(network.getInetAddresses()))
			{
				// Request port 0 so the server will pick an ephemeral port.
				addresses.add(new InetSocketAddress(address, 0));
			}
		}
		for (InetSocketAddress hostAddress : addresses)
		{
			System.out.println("Host as: " + hostAddress);
			RestServer server = new RestServer(hostAddress, new PathResource(dir));
			server.start();
			// Get the port so we can override the InetSocketAddress objects in the URL.
			int port = server.getPort();
			for (InetSocketAddress fetchAddress : addresses)
			{
				String hostPart = _addressAsUrlString(fetchAddress.getAddress());
				String url = "http://" + hostPart + ":" + port + "/temp.txt";
				System.out.print("\tTest as: " + url);
				boolean expectedSuccess = (fetchAddress == hostAddress);
				try
				{
					byte[] found = RestHelpers.get(url);
					byte[] expected = new byte[0];
					Assert.assertArrayEquals(expected, found);
					Assert.assertTrue(expectedSuccess);
					System.out.println(" -- SUCCESS");
				}
				catch (ConnectException e)
				{
					// We failed to connect.
					Assert.assertFalse(expectedSuccess);
					System.out.println(" -- FAILED (expected)");
				}
			}
			server.stop();
		}
	}

	@Test
	public void testAllInterfaces() throws Throwable {
		// Create the static resource.
		TemporaryFolder folder = new TemporaryFolder();
		folder.create();
		File dir = folder.newFolder();
		new File(dir, "temp.txt").createNewFile();
		// Host on all interfaces.
		RestServer server = new RestServer(new InetSocketAddress(8080), new PathResource(dir));
		
		// Find the interfaces.
		List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		// (we can only run the test if there is more than one interface).
		Assume.assumeTrue(interfaces.size() > 1);
		List<InetSocketAddress> addresses = new ArrayList<>();
		for (NetworkInterface network : interfaces)
		{
			for (InetAddress address : Collections.list(network.getInetAddresses()))
			{
				addresses.add(new InetSocketAddress(address, 8080));
			}
		}
		server.start();
		// Get the port to verify it matches.
		int port = server.getPort();
		for (InetSocketAddress fetchAddress : addresses)
		{
			String hostPart = _addressAsUrlString(fetchAddress.getAddress());
			Assert.assertEquals(port, fetchAddress.getPort());
			String url = "http://" + hostPart + ":" + fetchAddress.getPort() + "/temp.txt";
			byte[] found = RestHelpers.get(url);
			byte[] expected = new byte[0];
			Assert.assertArrayEquals(expected, found);
		}
		server.stop();
	}

	@Test
	public void testFailingWebSocket() throws Throwable {
		RestServer server = new RestServer(new InetSocketAddress(8080), null);
		boolean[] serverConnect = new boolean[1];
		boolean[] serverError = new boolean[1];
		boolean[] serverClose = new boolean[1];
		// Create a single end-point which will only pass if we give it a specific variable.
		server.addWebSocketFactory("/ws", 1, "filter", (JettyServerUpgradeRequest upgradeRequest, String[] variables) ->  "filter".equals(variables[0]) ? new NamedListener(variables) {
			@Override
			public void onConnect(String name, RemoteEndpoint endpoint)
			{
				serverConnect[0] = true;
			}
			@Override
			public void onError(String name, RemoteEndpoint endpoint, Throwable error)
			{
				serverError[0] = true;
			}
			@Override
			public void onBinary(String name, RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.fail();
			}
			@Override
			public void onText(String name, RemoteEndpoint endpoint, String message)
			{
				Assert.fail();
			}
			@Override
			public void onClose()
			{
				serverClose[0] = true;
			}
		} : null);
		server.start();
		
		boolean[] clientError = new boolean[1];
		
		ProtocolClient failClient = new ProtocolClient("ws://localhost:8080/ws/bogus", "filter") {
			@Override
			public void onError(RemoteEndpoint endpoint, Throwable error)
			{
				clientError[0] = true;
			}
			@Override
			public void onBinary(RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.fail();
			}
			@Override
			public void onText(RemoteEndpoint endpoint, String message)
			{
				Assert.fail();
			}
		};
		
		boolean didFail = false;
		try
		{
			failClient.waitForConnect();
		}
		catch (UpgradeException e)
		{
			// Expected.
			didFail = true;
		}
		Assert.assertTrue(didFail);
		failClient.stop();
		// The failing one should only see the client-side error, but no server-side connect, error, or close.
		Assert.assertTrue(clientError[0]);
		Assert.assertFalse(serverConnect[0]);
		Assert.assertFalse(serverError[0]);
		Assert.assertFalse(serverClose[0]);
		
		clientError[0] = false;
		ProtocolClient passClient = new ProtocolClient("ws://localhost:8080/ws/filter", "filter") {
			@Override
			public void onError(RemoteEndpoint endpoint, Throwable error)
			{
				clientError[0] = true;
			}
			@Override
			public void onBinary(RemoteEndpoint endpoint, byte[] payload, int offset, int len)
			{
				Assert.fail();
			}
			@Override
			public void onText(RemoteEndpoint endpoint, String message)
			{
				Assert.fail();
			}
		};
		passClient.waitForConnect();
		passClient.stop();
		// The passing one should see no errors but normal connect and disconnect.
		Assert.assertFalse(clientError[0]);
		Assert.assertTrue(serverConnect[0]);
		Assert.assertFalse(serverError[0]);
		Assert.assertTrue(serverClose[0]);
		server.stop();
	}


	private String _sendRequest(HttpClient httpClient, HttpMethod method, String url, String loggedInUserName) throws Throwable {
		Request request = httpClient.newRequest(url);
		request.method(method);
		request.body(new StringRequestContent(loggedInUserName));
		String content = new String(request.send().getContent(), StandardCharsets.UTF_8);
		return content;
	}

	private static String _addressAsUrlString(InetAddress address)
	{
		// I feel like there is probably a helper somewhere to do this, but I can't find it.
		byte[] raw = address.getAddress();
		String result = null;
		boolean isv6 = (16 == raw.length);
		if (isv6)
		{
			String working = "";
			int extent = 0;
			for (byte b : raw)
			{
				if (2 == extent)
				{
					working += ":";
					extent = 0;
				}
				int i = Byte.toUnsignedInt(b);
				String hex = Integer.toHexString(i);
				if (1 == hex.length())
				{
					working += "0";
				}
				working += hex;
				extent += 1;
			}
			result = "[" + working + "]";
		}
		else
		{
			Assert.assertEquals(4, raw.length);
			String working = "";
			int extent = 0;
			for (byte b : raw)
			{
				if (1 == extent)
				{
					working += ".";
					extent = 0;
				}
				int i = Byte.toUnsignedInt(b);
				working += i;
				extent += 1;
			}
			result = working;
		}
		return result;
	}


	public static abstract class NamedListener implements WebSocketListener {
		private final String _name;
		private RemoteEndpoint _endpoint;
		
		public NamedListener(String[] variables) {
			_name = variables[0];
		}
		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			this.onClose();
		}
		@Override
		public void onWebSocketConnect(Session session) {
			_endpoint = session.getRemote();
			this.onConnect(_name, _endpoint);
		}
		@Override
		public void onWebSocketError(Throwable cause) {
			this.onError(_name, _endpoint, cause);
		}
		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			this.onBinary(_name, _endpoint, payload, offset, len);
		}
		@Override
		public void onWebSocketText(String message) {
			this.onText(_name, _endpoint, message);
		}
		public abstract void onConnect(String name, RemoteEndpoint endpoint);
		public abstract void onError(String name, RemoteEndpoint endpoint, Throwable error);
		public abstract void onBinary(String name, RemoteEndpoint endpoint, byte[] payload, int offset, int len);
		public abstract void onText(String name, RemoteEndpoint endpoint, String message);
		public abstract void onClose();
	}


	public static abstract class ProtocolClient implements WebSocketListener
	{
		private final WebSocketClient _client;
		private RemoteEndpoint _endpoint;
		private Throwable _error;
		public ProtocolClient(String uri, String protocol) throws Exception
		{
			_client = new WebSocketClient();
			_client.start();
			ClientUpgradeRequest req = new ClientUpgradeRequest();
			req.setSubProtocols(protocol);
			_client.connect(this, new URI(uri), req);
		}
		public void sendText(String text) throws IOException
		{
			_endpoint.sendString(text);
		}
		public void sendBinary(byte[] binary) throws IOException
		{
			_endpoint.sendBytes(ByteBuffer.wrap(binary));
		}
		public synchronized void waitForConnect() throws Throwable
		{
			while ((null == _endpoint) && (null == _error))
			{
				this.wait();
			}
			if (null != _error)
			{
				throw _error;
			}
		}
		public void stop() throws Exception
		{
			_client.stop();
		}
		@Override
		public void onWebSocketClose(int statusCode, String reason) {
		}
		@Override
		public synchronized void onWebSocketConnect(Session session) {
			_endpoint = session.getRemote();
			this.notifyAll();
		}
		@Override
		public synchronized void onWebSocketError(Throwable cause) {
			// Store and notify in case we are stuck in a waitForConnect
			_error = cause;
			this.notifyAll();
			
			this.onError(_endpoint, cause);
		}
		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			this.onBinary(_endpoint, payload, offset, len);
		}
		@Override
		public void onWebSocketText(String message) {
			this.onText(_endpoint, message);
		}
		public abstract void onError(RemoteEndpoint endpoint, Throwable error);
		public abstract void onBinary(RemoteEndpoint endpoint, byte[] payload, int offset, int len);
		public abstract void onText(RemoteEndpoint endpoint, String message);
	}
}
