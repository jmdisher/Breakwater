package com.jeffdisher.breakwater;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.jeffdisher.breakwater.paths.ConstantPathParser;
import com.jeffdisher.breakwater.paths.IPathParser;
import com.jeffdisher.breakwater.paths.StringPathParser;
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
	
	private final Map<String, IPathParser> _pathParsers;

	/**
	 * Creates a new RestServer, ready to be started with start() once handlers have been installed.
	 * 
	 * @param bindAddress The interface to bind.
	 * @param staticContentResource The description of how to handle static resources (no static if null).
	 * @param cacheControl The cache control string for the static resources (default if null - 
	 * "no-store,no-cache,must-revalidate" is good for disabling).
	 * @param staticContentResource Handler for static resources (null if no general static handling required).
	 */
	public RestServer(InetSocketAddress bindAddress
			, Resource staticContentResource
			, String cacheControl
	)
	{
		_entryPoint = new EntryPoint();
		_server = new Server(bindAddress);
		
		// Create the static resource handler.
		ResourceHandler staticResources = null;
		if (null != staticContentResource) {
			staticResources = new ResourceHandler();
			staticResources.setBaseResource(staticContentResource);
			if (null != cacheControl)
			{
				staticResources.setCacheControl(cacheControl);
			}
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
		
		// Setup the path parsers with the built-in types.
		_pathParsers = new HashMap<>();
		// "string" matches on any string path component.
		_pathParsers.put("string", new StringPathParser());
	}

	/**
	 * Installs a custom handler for data types in inline paths.  If "name" is the type, it can be referenced as
	 * "{name}" in the paths.
	 * 
	 * @param name The name used to identify the parser in paths.
	 * @param parser The parser to use to interpret data in these path components.
	 */
	public void installPathParser(String name, IPathParser parser)
	{
		Assert.assertTrue(name.length() > 0);
		Assert.assertTrue(null != parser);
		Assert.assertTrue(!_pathParsers.containsKey(name));
		_pathParsers.put(name, parser);
	}

	public void addDeleteHandler(String path, IDeleteHandler handler)
	{
		_deleteHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addGetHandler(String path, IGetHandler handler)
	{
		_getHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addPostFormHandler(String path, IPostFormHandler handler)
	{
		_postFormHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addPostMultiPartHandler(String path, IPostMultiPartHandler handler)
	{
		_postMultiPartHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addPostRawHandler(String path, IPostRawHandler handler)
	{
		_postRawHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addPutHandler(String path, IPutHandler handler)
	{
		_putHandlers.add(0, new HandlerTuple<>(_parsePath(path), handler));
	}

	public void addWebSocketFactory(String path, String protocolName, IWebSocketFactory factory)
	{
		_webSocketFactories.add(0, new WebSocketFactoryTuple(_parsePath(path), protocolName, factory));
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
			OneMatch<IGetHandler> matched = _findMatch(_getHandlers, target);
			if (null != matched)
			{
				matched.handler.handle(request, response, matched.matched);
				found = true;
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
				OneMatch<IPostMultiPartHandler> matched = _findMatch(_postMultiPartHandlers, target);
				if (null != matched)
				{
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
					matched.handler.handle(request, response, matched.matched, parts);
					found = true;
				}
			}
			else if (isFormEncoded)
			{
				OneMatch<IPostFormHandler> matched = _findMatch(_postFormHandlers, target);
				if (null != matched)
				{
					StringMultiMap<String> form = new StringMultiMap<>();
					MultiMap<String> parsed = new MultiMap<String>();
					UrlEncoded.decodeTo(request.getInputStream(), parsed, StandardCharsets.UTF_8, MAX_POST_SIZE, MAX_VARIABLES);
					for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
						String key = entry.getKey();
						for (String value : entry.getValue()) {
							form.append(key, value);
						}
					}
					matched.handler.handle(request, response, matched.matched, form);
					found = true;
				}
			}
			else
			{
				OneMatch<IPostRawHandler> matched = _findMatch(_postRawHandlers, target);
				if (null != matched)
				{
					// In this case, the user will need to read the data directly from the input stream in request.
					matched.handler.handle(request, response, matched.matched);
					found = true;
				}
			}
			return found;
		}
		private boolean _handlePut(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
		{
			boolean found = false;
			OneMatch<IPutHandler> matched = _findMatch(_putHandlers, target);
			if (null != matched)
			{
				matched.handler.handle(request, response, matched.matched, request.getInputStream());
				found = true;
			}
			return found;
		}
		private boolean _handleDelete(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
		{
			boolean found = false;
			OneMatch<IDeleteHandler> matched = _findMatch(_deleteHandlers, target);
			if (null != matched)
			{
				matched.handler.handle(request, response, matched.matched);
				found = true;
			}
			return found;
		}
		private WebSocketListener _handleWebSocketUpgrade(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp)
		{
			String target = req.getRequestPath();
			IWebSocketFactory matchedFactory = null;
			Object[] matchedComponents = null;
			for (WebSocketFactoryTuple tuple : _webSocketFactories) {
				Object[] possible = tuple.matcher.handle(target);
				if (null != possible)
				{
					// We know that we can handle this path so select the protocols.
					boolean didMatch = false;
					for (String subProtocol : req.getSubProtocols())
					{
						if (tuple.protocolName.equals(subProtocol))
						{
							didMatch = true;
						}
					}
					if (didMatch)
					{
						if (null != matchedFactory)
						{
							// This is a static configuration error but we just log it and fail to interpret.
							System.err.println("Ambiguous parse for WebSocket target: \"" + target + "\"");
							matchedFactory = null;
							matchedComponents = null;
							break;
						}
						else
						{
							matchedFactory = tuple.factory;
							matchedComponents = possible;
						}
					}
				}
			}
			
			// Create the factory if we had a single match.
			return (null != matchedFactory)
					? matchedFactory.create(req, matchedComponents)
					: null
			;
		}
	}

	private IPathParser[] _parsePath(String path)
	{
		// We always expect a path to start with a / but never end with one.
		Assert.assertTrue(path.startsWith("/"));
		Assert.assertTrue(!path.endsWith("/"));
		// We want to permit empty trailing paths as a constant handler so use a negative limit.
		String[] parts = path.split("/", -1);
		IPathParser[] parsers = new IPathParser[parts.length - 1];
		for (int i = 0; i < parsers.length; ++i)
		{
			String part = parts[i + 1];
			if (part.contains("{"))
			{
				// This is something which must be a variable type.
				Assert.assertTrue(part.startsWith("{"));
				Assert.assertTrue(part.endsWith("}"));
				String check = part.substring(1, part.length() - 1);
				parsers[i] = _pathParsers.get(check);
				if (null == parsers[i])
				{
					throw new IllegalArgumentException("Type not known: " + check);
				}
			}
			else
			{
				parsers[i] = new ConstantPathParser(part);
			}
		}
		return parsers;
	}

	private static <T> OneMatch<T> _findMatch(List<HandlerTuple<T>> handlers, String target)
	{
		OneMatch<T> matched = null;
		for (HandlerTuple<T> tuple : handlers) {
			Object[] possible = tuple.matcher.handle(target);
			if (null != possible)
			{
				if (null != matched)
				{
					// This is a static configuration error but we just log it and fail to interpret.
					System.err.println("Ambiguous parse for target: \"" + target + "\"");
					matched = null;
					break;
				}
				else
				{
					matched = new OneMatch<T>(tuple.handler, possible);
				}
			}
		}
		return matched;
	}


	private static class HandlerTuple<T> {
		public final PathMatcher matcher;
		public final T handler;
		
		public HandlerTuple(IPathParser[] parsers, T handler) {
			this.matcher = new PathMatcher(parsers);
			this.handler = handler;
		}
	}


	private static class WebSocketFactoryTuple {
		public final PathMatcher matcher;
		public final String protocolName;
		public final IWebSocketFactory factory;
		
		public WebSocketFactoryTuple(IPathParser[] parsers, String protocolName, IWebSocketFactory factory) {
			this.matcher = new PathMatcher(parsers);
			this.protocolName = protocolName;
			this.factory = factory;
		}
	}


	private static class PathMatcher {
		private final IPathParser[] _parsers;
		
		public PathMatcher(IPathParser[] parsers) {
			_parsers = parsers;
		}
		
		public Object[] handle(String target) {
			Object[] matched = null;
			Assert.assertTrue(target.startsWith("/"));
			// We do want to include the final path component, even if empty, so use a negative limit.
			String[] parts = target.split("/", -1);
			if (_parsers.length == (parts.length - 1))
			{
				matched = new Object[_parsers.length];
				for (int i = 0; i < matched.length; ++i)
				{
					try
					{
						String raw = URLDecoder.decode(parts[i + 1], StandardCharsets.UTF_8);
						matched[i] = _parsers[i].parse(raw);
					}
					catch (Throwable t)
					{
						// This will just fall into the null check.
					}
					if (null == matched[i])
					{
						matched = null;
						break;
					}
				}
			}
			return matched;
		}
	}


	private static class OneMatch<T>
	{
		private final T handler;
		private final Object[] matched;
		public OneMatch(T handler, Object[] matched)
		{
			this.handler = handler;
			this.matched = matched;
		}
	}
}
