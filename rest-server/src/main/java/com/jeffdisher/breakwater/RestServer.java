package com.jeffdisher.breakwater;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
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
	private final List<HandlerTuple<IPostFormHandler>> _postFormHandlers;
	private final List<HandlerTuple<IPostMultiPartHandler>> _postMultiPartHandlers;
	private final List<HandlerTuple<IPostRawHandler>> _postRawHandlers;
	private final List<HandlerTuple<IPutHandler>> _putHandlers;
	private final List<WebSocketFactoryTuple> _webSocketFactories;

	/**
	 * Creates a new RestServer, ready to be started with start() once handlers have been installed.
	 * 
	 * @param bindAddress The interface to bind.
	 * @param port The port on which to listen.
	 * @param staticContentResource Handler for static resources (null if no general static handling required).
	 */
	public RestServer(InetSocketAddress bindAddress, Resource staticContentResource) {
		_entryPoint = new EntryPoint();
		_server = new Server(bindAddress);
		
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
		_postFormHandlers = new ArrayList<>();
		_postMultiPartHandlers = new ArrayList<>();
		_postRawHandlers = new ArrayList<>();
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

	public void addPostFormHandler(String pathPrefix, int variableCount, IPostFormHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_postFormHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void addPostMultiPartHandler(String pathPrefix, int variableCount, IPostMultiPartHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_postMultiPartHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
	}

	public void addPostRawHandler(String pathPrefix, int variableCount, IPostRawHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_postRawHandlers.add(0, new HandlerTuple<>(pathPrefix, variableCount, handler));
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

	/**
	 * @return The port the server bound for listening (undefined before start()).
	 */
	public int getPort()
	{
		return _server.getURI().getPort();
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
			
			// This line may include things like boundary, etc, so it can't be strict equality.
			String contentType = request.getContentType();
			boolean isMultiPart = (null != contentType) && contentType.startsWith("multipart/form-data");
			boolean isFormEncoded = (null != contentType) && contentType.startsWith("application/x-www-form-urlencoded");
			
			if (isMultiPart)
			{
				for (HandlerTuple<IPostMultiPartHandler> tuple : _postMultiPartHandlers) {
					if (tuple.matcher.canHandle(target)) {
						String[] variables = tuple.matcher.parseVariables(target);
						StringMultiMap<byte[]> parts = new StringMultiMap<>();
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
						tuple.handler.handle(request, response, variables, parts);
						found = true;
						break;
					}
				}
			}
			else if (isFormEncoded)
			{
				for (HandlerTuple<IPostFormHandler> tuple : _postFormHandlers) {
					if (tuple.matcher.canHandle(target)) {
						String[] variables = tuple.matcher.parseVariables(target);
						StringMultiMap<String> form = new StringMultiMap<>();
						MultiMap<String> parsed = new MultiMap<String>();
						UrlEncoded.decodeTo(request.getInputStream(), parsed, StandardCharsets.UTF_8, MAX_POST_SIZE, MAX_VARIABLES);
						for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
							String key = entry.getKey();
							for (String value : entry.getValue()) {
								form.append(key, value);
							}
						}
						tuple.handler.handle(request, response, variables, form);
						found = true;
						break;
					}
				}
			}
			else
			{
				for (HandlerTuple<IPostRawHandler> tuple : _postRawHandlers) {
					if (tuple.matcher.canHandle(target)) {
						String[] variables = tuple.matcher.parseVariables(target);
						
						// In this case, the user will need to read the data directly from the input stream in request.
						tuple.handler.handle(request, response, variables);
						found = true;
						break;
					}
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
						socket = tuple.factory.create(req, variables);
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
			for (int i = 0; i < variables.length; ++i)
			{
				variables[i] = URLDecoder.decode(variables[i], StandardCharsets.UTF_8);
			}
			return variables;
		}
	}
}
