package com.jeffdisher.breakwater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringRequestContent;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Assert;
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
		RestServer server = new RestServer(8080, null);
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
		RestServer server = new RestServer(8080, null);
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
		RestServer server = new RestServer(8080, null);
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
		RestServer server = new RestServer(8080, null);
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
		RestServer server = new RestServer(8080, null);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080, null);
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
		RestServer server = new RestServer(8080, null);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080, null);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080, null);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + rawPost.length);
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
		RestServer server = new RestServer(8080, null);
		HttpClient httpClient = new HttpClient();
		server.addPostHandler("/start", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
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
		RestServer server = new RestServer(8080, new PathResource(dir));
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
		Throwable[] failureReference = new Throwable[1];
		RestServer server = new RestServer(8080, null);
		// We want to create a server with 2 protocols, at the same root address, so make sure that we can resolve multiple protocols.
		server.addWebSocketFactory("/ws", 1, "textPrefix", (String[] variables) -> new NamedListener(variables) {
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
		server.addWebSocketFactory("/ws", 1, "binaryRotate", (String[] variables) -> new NamedListener(variables) {
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
		textClient.stop();
		binaryClient.stop();
		closeLatch.await();
		server.stop();
		
		Assert.assertNull(failureReference[0]);
		Assert.assertEquals("prefix_testText", textOut[0]);
		Assert.assertArrayEquals(new byte[] { 16, 17, 18, 19 }, binaryOut);
	}


	private String _sendRequest(HttpClient httpClient, HttpMethod method, String url, String loggedInUserName) throws Throwable {
		Request request = httpClient.newRequest(url);
		request.method(method);
		request.body(new StringRequestContent(loggedInUserName));
		String content = new String(request.send().getContent(), StandardCharsets.UTF_8);
		return content;
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
		public synchronized void waitForConnect() throws InterruptedException
		{
			while (null == _endpoint)
			{
				this.wait();
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
		public void onWebSocketError(Throwable cause) {
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
