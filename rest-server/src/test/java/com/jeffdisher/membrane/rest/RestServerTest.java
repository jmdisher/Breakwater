package com.jeffdisher.membrane.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
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
		HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8080/test1").openConnection();
		Assert.assertEquals("TESTING".length(), connection.getContentLength());
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
		HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8080/test2").openConnection();
		Assert.assertEquals(404, connection.getResponseCode());
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
		HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8080/test1").openConnection();
		Assert.assertEquals("TESTING".length(), connection.getContentLength());
		connection = (HttpURLConnection)new URL("http://localhost:8080/test2/TEST").openConnection();
		Assert.assertEquals("TEST".length(), connection.getContentLength());
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
		HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8080/test").openConnection();
		connection.setRequestMethod("PUT");
		byte[] buffer = new byte[] {1,2,3,4,5};
		connection.setDoOutput(true);
		connection.getOutputStream().write(buffer);
		Assert.assertEquals(buffer.length, connection.getContentLength());
		stopLatch.await();
		server.stop();
	}

	@Test
	public void testPostVars() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(8080);
		server.addPostHandler("/test", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables, Map<String, String[]> postVariables) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("" + postVariables.size());
				stopLatch.countDown();
			}});
		server.start();
		HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8080/test").openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
		writer.write("var1=val1&var2=&var1=val2");
		writer.flush();
		// We expect it to write "2", since there are 2 top-level keys.
		Assert.assertEquals("2".getBytes()[0], (byte)connection.getInputStream().read());
		stopLatch.await();
		server.stop();
	}
}
