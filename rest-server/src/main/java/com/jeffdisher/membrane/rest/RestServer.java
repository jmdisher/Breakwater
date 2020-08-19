package com.jeffdisher.membrane.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

import com.jeffdisher.laminar.utils.Assert;


public class RestServer {
	private final static int MAX_POST_SIZE = 64 * 1024;
	private final static int MAX_VARIABLES = 16;

	private final EntryPoint _entryPoint;
	private final Server _server;
	private final List<HandlerTuple<IDeleteHandler>> _deleteHandlers;
	private final List<HandlerTuple<IGetHandler>> _getHandlers;
	private final List<HandlerTuple<IPostHandler>> _postHandlers;
	private final List<HandlerTuple<IPutHandler>> _putHandlers;

	public RestServer(int port) {
		_entryPoint = new EntryPoint();
		_server = new Server();
		ServerConnector connector = new ServerConnector(_server);
		connector.setPort(port);
		_server.setConnectors(new ServerConnector[] { connector });
		// Hard to find this missing step in session startup:  https://www.programcreek.com/java-api-examples/index.php?api=org.eclipse.jetty.server.session.SessionHandler
		SessionHandler sessionHandler = new SessionHandler();
		sessionHandler.setHandler(_entryPoint);
		_server.setHandler(sessionHandler);
		_deleteHandlers = new ArrayList<>();
		_getHandlers = new ArrayList<>();
		_postHandlers = new ArrayList<>();
		_putHandlers = new ArrayList<>();
	}

	public void addDeleteHandler(String pathPrefix, int variableCount, IDeleteHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_deleteHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void addGetHandler(String pathPrefix, int variableCount, IGetHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_getHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void addPostHandler(String pathPrefix, int variableCount, IPostHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_postHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void addPutHandler(String pathPrefix, int variableCount, IPutHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_putHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void start() {
		try {
			_server.start();
		} catch (Exception e) {
			// This example doesn't handle failures.
			throw Assert.unexpected(e);
		}
	}

	public void stop() {
		try {
			_server.stop();
			_server.join();
		} catch (Exception e) {
			// This example doesn't handle failures.
			throw Assert.unexpected(e);
		}
	}


	private class EntryPoint extends AbstractHandler {
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			String method = baseRequest.getMethod();
			if ("DELETE".equals(method)) {
				for (HandlerTuple<IDeleteHandler> tuple : _deleteHandlers) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(request, response, variables);
						baseRequest.setHandled(true);
						break;
					}
				}
			} else if ("GET".equals(method)) {
				for (HandlerTuple<IGetHandler> tuple : _getHandlers) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(request, response, variables);
						baseRequest.setHandled(true);
						break;
					}
				}
			} else if ("POST".equals(method)) {
				for (HandlerTuple<IPostHandler> tuple : _postHandlers) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						StringMultiMap<byte[]> parts = null;
						StringMultiMap<String> form = null;
						byte[] raw = null;
						// This line may include things like boundary, etc, so it can't be strict equality.
						String contentType = request.getContentType();
						boolean isMultiPart = (null != contentType) && contentType.startsWith("multipart/form-data");
						boolean isFormEncoded = (null != contentType) && contentType.startsWith("application/x-www-form-urlencoded");
						
						if (isMultiPart) {
							parts = new StringMultiMap<>();
							request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement(System.getProperty("java.io.tmpdir"), MAX_POST_SIZE, MAX_POST_SIZE, MAX_POST_SIZE + 1));
							for (Part part : request.getParts()) {
								String name = part.getName();
								Assert.assertTrue(part.getSize() <= (long)MAX_POST_SIZE);
								byte[] data = new byte[(int)part.getSize()];
								if (data.length > 0) {
									InputStream stream = part.getInputStream();
									int didRead = stream.read(data);
									while (didRead < data.length) {
										didRead += stream.read(data, didRead, data.length - didRead);
									}
								}
								parts.append(name, data);
								part.delete();
								if (parts.valueCount() > MAX_VARIABLES) {
									// We will only read the first MAX_VARIABLES, much like the form-encoded.
									break;
								}
							}
						} else if (isFormEncoded) {
							form = new StringMultiMap<>();
							MultiMap<String> parsed = new MultiMap<String>();
							UrlEncoded.decodeTo(request.getInputStream(), parsed, StandardCharsets.UTF_8, MAX_POST_SIZE, MAX_VARIABLES);
							for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
								String key = entry.getKey();
								for (String value : entry.getValue()) {
									form.append(key, value);
								}
							}
						} else {
							// Note that we can't rely on the content length since the client may not send it so only allow what we would normally allow for a single variable entry.
							ByteArrayOutputStream holder = new ByteArrayOutputStream();
							InputStream stream = request.getInputStream();
							boolean keepReading = true;
							byte[] temp = new byte[1024];
							int bytesRead = 0;
							while (keepReading) {
								int didRead = stream.read(temp);
								if (didRead > 0) {
									holder.write(temp, 0, didRead);
									bytesRead += didRead;
									if (bytesRead >= MAX_POST_SIZE) {
										keepReading = false;
									}
								} else {
									keepReading = false;
								}
							}
							int validSize = (bytesRead > MAX_POST_SIZE)
									? MAX_POST_SIZE
									: bytesRead;
							raw = new byte[validSize];
							System.arraycopy(holder.toByteArray(), 0, raw, 0, validSize);
						}
						tuple.handler.handle(request, response, variables, form, parts, raw);
						baseRequest.setHandled(true);
						break;
					}
				}
			} else if ("PUT".equals(method)) {
				for (HandlerTuple<IPutHandler> tuple : _putHandlers) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(request, response, variables, baseRequest.getInputStream());
						baseRequest.setHandled(true);
						break;
					}
				}
			}
		}
	}


	private static class HandlerTuple<T> {
		private final String _pathPrefix;
		private final int _variableCount;
		public final T handler;
		
		public HandlerTuple(String pathPrefix, int variableCount, T handler) {
			_pathPrefix = pathPrefix;
			_variableCount = variableCount;
			this.handler = handler;
		}
		
		public boolean canHandle(String method, String target) {
			return target.startsWith(_pathPrefix) && ((target.substring(_pathPrefix.length()).split("/").length - 1) == _variableCount);
		}
		
		public String[] parseVariables(String target) {
			String variableString = target.substring(_pathPrefix.length());
			String[] variables = new String[_variableCount];
			System.arraycopy(variableString.split("/"), 1, variables, 0, _variableCount);
			return variables;
		}
	}
}
