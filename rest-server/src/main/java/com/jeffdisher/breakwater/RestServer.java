package com.jeffdisher.breakwater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import com.jeffdisher.breakwater.utilities.Assert;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;


public class RestServer {
	private final static int MAX_POST_SIZE = 64 * 1024;
	private final static int MAX_VARIABLES = 16;

	private final EntryPoint _entryPoint;
	private final Server _server;
	private final List<HandlerTuple<IDeleteHandler>> _deleteHandlers;
	private final List<HandlerTuple<IGetHandler>> _getHandlers;
	private final List<HandlerTuple<IPostHandler>> _postHandlers;
	private final List<HandlerTuple<IPutHandler>> _putHandlers;
	private final List<WebSocketFactoryTuple> _webSocketFactories;

	public RestServer(int port, Resource staticContentResource) {
		_entryPoint = new EntryPoint();
		_server = new Server();
		ServerConnector connector = new ServerConnector(_server);
		connector.setPort(port);
		_server.setConnectors(new ServerConnector[] { connector });
		
		// Create the static resource handler.
		ResourceHandler staticResources = null;
		if (null != staticContentResource) {
			staticResources = new ResourceHandler();
			staticResources.setBaseResource(staticContentResource);
		}
		
		// We need to create a ServletContextHandler in order to check the request path in web socket connections and we will request that it enables session management.
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.addServlet(new ServletHolder(_entryPoint), "/*");
		
		// We also want to enable WebSockets.
		JettyWebSocketServletContainerInitializer.configure(context, null);
		
		if (null != staticResources) {
			HandlerList list = new HandlerList();
			list.addHandler(staticResources);
			list.addHandler(context);
			_server.setHandler(list);
		} else {
			_server.setHandler(context);
		}
		
		_deleteHandlers = new ArrayList<>();
		_getHandlers = new ArrayList<>();
		_postHandlers = new ArrayList<>();
		_putHandlers = new ArrayList<>();
		_webSocketFactories = new ArrayList<>();
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

	public void addWebSocketFactory(String pathPrefix, int variableCount, String protocolName, IWebSocketFactory factory) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_webSocketFactories.add(0, new WebSocketFactoryTuple(pathPrefix, variableCount, protocolName, factory));
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


	private class EntryPoint extends JettyWebSocketServlet {
		private static final long serialVersionUID = 1L;
		
		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
		{
			String target = request.getPathInfo();
			boolean found = _handleGet(target, request, response);
			if (!found)
			{
				// We will use 404 since calling super gives 405, which isn't generally what we want (since GET is clearly 404).
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		@Override
		protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
		{
			String target = request.getPathInfo();
			boolean found = _handlePost(target, request, response);
			if (!found)
			{
				// We will use 404 since calling super gives 405, which isn't generally what we want (since GET is clearly 404).
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		@Override
		protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
		{
			String target = request.getPathInfo();
			boolean found = _handlePut(target, request, response);
			if (!found)
			{
				// We will use 404 since calling super gives 405, which isn't generally what we want (since GET is clearly 404).
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		@Override
		protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
		{
			String target = request.getPathInfo();
			boolean found = _handleDelete(target, request, response);
			if (!found)
			{
				// We will use 404 since calling super gives 405, which isn't generally what we want (since GET is clearly 404).
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		@Override
		protected void configure(JettyWebSocketServletFactory factory)
		{
			// Note:  This is called once during startup.
			factory.setIdleTimeout(Duration.ofMillis(10_000L));
			factory.setCreator(new JettyWebSocketCreator() {
				@Override
				public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp)
				{
					return _handleWebSocketUpgrade(req, resp);
				}
			});
		}
		
		private boolean _handleGet(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
		{
			boolean found = false;
			for (HandlerTuple<IGetHandler> tuple : _getHandlers) {
				if (tuple.matcher.canHandle(target)) {
					String[] variables = tuple.matcher.parseVariables(target);
					tuple.handler.handle(request, response, variables);
					found = true;
					break;
				}
			}
			return found;
		}
		private boolean _handlePost(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
		{
			boolean found = false;
			for (HandlerTuple<IPostHandler> tuple : _postHandlers) {
				if (tuple.matcher.canHandle(target)) {
					String[] variables = tuple.matcher.parseVariables(target);
					StringMultiMap<byte[]> parts = null;
					StringMultiMap<String> form = null;
					byte[] raw = null;
					// This line may include things like boundary, etc, so it can't be strict equality.
					String contentType = request.getContentType();
					boolean isMultiPart = (null != contentType) && contentType.startsWith("multipart/form-data");
					boolean isFormEncoded = (null != contentType) && contentType.startsWith("application/x-www-form-urlencoded");
					
					if (isMultiPart) {
						parts = new StringMultiMap<>();
						request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement(System.getProperty("java.io.tmpdir"), MAX_POST_SIZE, MAX_POST_SIZE, MAX_POST_SIZE + 1));
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
					found = true;
					break;
				}
			}
			return found;
		}
		private boolean _handlePut(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
		{
			boolean found = false;
			for (HandlerTuple<IPutHandler> tuple : _putHandlers) {
				if (tuple.matcher.canHandle(target)) {
					String[] variables = tuple.matcher.parseVariables(target);
					tuple.handler.handle(request, response, variables, request.getInputStream());
					found = true;
					break;
				}
			}
			return found;
		}
		private boolean _handleDelete(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
		{
			boolean found = false;
			for (HandlerTuple<IDeleteHandler> tuple : _deleteHandlers) {
				if (tuple.matcher.canHandle(target)) {
					String[] variables = tuple.matcher.parseVariables(target);
					tuple.handler.handle(request, response, variables);
					found = true;
					break;
				}
			}
			return found;
		}
		private WebSocketListener _handleWebSocketUpgrade(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp)
		{
			WebSocketListener socket = null;
			String target = req.getRequestPath();
			for (WebSocketFactoryTuple tuple : _webSocketFactories) {
				if (tuple.matcher.canHandle(target)) {
					// We know that we can handle this path so select the protocols.
					boolean didMatch = false;
					for (String subProtocol : req.getSubProtocols()) {
						if (tuple.protocolName.equals(subProtocol))
						{
							resp.setAcceptedSubProtocol(subProtocol);
							// We currently only support a given factory only being able to build WebSockets which implement a single protocol.
							didMatch = true;
							break;
						}
					}
					if (didMatch)
					{
						String[] variables = tuple.matcher.parseVariables(target);
						socket = tuple.factory.create(variables);
						break;
					}
				}
			}
			return socket;
		}
	}


	private static class HandlerTuple<T> {
		public final PathMatcher matcher;
		public final T handler;
		
		public HandlerTuple(String pathPrefix, int variableCount, T handler) {
			this.matcher = new PathMatcher(pathPrefix, variableCount);
			this.handler = handler;
		}
	}


	private static class WebSocketFactoryTuple {
		public final PathMatcher matcher;
		public final String protocolName;
		public final IWebSocketFactory factory;
		
		public WebSocketFactoryTuple(String pathPrefix, int variableCount, String protocolName, IWebSocketFactory factory) {
			this.matcher = new PathMatcher(pathPrefix, variableCount);
			this.protocolName = protocolName;
			this.factory = factory;
		}
	}


	private static class PathMatcher {
		private final String _pathPrefix;
		private final int _variableCount;
		
		public PathMatcher(String pathPrefix, int variableCount) {
			_pathPrefix = pathPrefix;
			_variableCount = variableCount;
		}
		
		public boolean canHandle(String target) {
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
