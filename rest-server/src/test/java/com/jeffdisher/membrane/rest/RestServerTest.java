package com.jeffdisher.membrane.rest;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;


public class RestServerTest {
	@Test
	public void testBasicHandle() throws Throwable {
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(8080);
		server.addHandler("GET", "/test1", 0, new IHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables, String inputLine) throws IOException {
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
		server.addHandler("GET", "/test1", 0, new IHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables, String inputLine) throws IOException {
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
		server.addHandler("GET", "/test1", 0, new IHandler() {
			@Override
			public void handle(HttpServletResponse response, String[] variables, String inputLine) throws IOException {
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("TESTING");
				server.addHandler("GET", "/test2", 1, new IHandler() {
					@Override
					public void handle(HttpServletResponse response, String[] variables, String inputLine) throws IOException {
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

}
