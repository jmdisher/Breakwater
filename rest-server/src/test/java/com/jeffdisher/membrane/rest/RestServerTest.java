package com.jeffdisher.membrane.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;


public class RestServerTest {
	@Test
	public void testBasicHandle() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(8080);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addGetHandler("/test1", 0, new IGetHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("TESTING");
				server.addGetHandler("/test2", 1, new IGetHandler() {
					@Override
					public void handle(HttpServletResponse response, String[] variables) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addPutHandler("/test", 0, new IPutHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables, InputStream inputStream) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addDeleteHandler("/test", 0, new IDeleteHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] pathVariables) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
		RestServer server = new RestServer(8080);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException {
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
}
