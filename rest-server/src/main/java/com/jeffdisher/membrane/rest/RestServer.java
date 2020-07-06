package com.jeffdisher.membrane.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.jeffdisher.laminar.utils.Assert;


public class RestServer {
	private final EntryPoint _entryPoint;
	private final Server _server;
	private List<HandlerTuple<IDeleteHandler>> _deleteHandlers;
	private List<HandlerTuple<IGetHandler>> _getHandlers;
	private List<HandlerTuple<IPostHandler>> _postHandlers;
	private List<HandlerTuple<IPutHandler>> _putHandlers;

	public RestServer(int port) {
		_entryPoint = new EntryPoint();
		_server = new Server();
		ServerConnector connector = new ServerConnector(_server);
		connector.setPort(port);
		_server.setConnectors(new ServerConnector[] { connector });
		_server.setHandler(_entryPoint);
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
				// Remove the handlers in case we need to add more in processing.
				List<HandlerTuple<IDeleteHandler>> handlersCopy = _deleteHandlers;
				_deleteHandlers = new ArrayList<>();
				
				for (HandlerTuple<IDeleteHandler> tuple : handlersCopy) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(response, variables);
						baseRequest.setHandled(true);
					}
				}
				_deleteHandlers.addAll(handlersCopy);
			} else if ("GET".equals(method)) {
				// Remove the handlers in case we need to add more in processing.
				List<HandlerTuple<IGetHandler>> handlersCopy = _getHandlers;
				_getHandlers = new ArrayList<>();
				
				for (HandlerTuple<IGetHandler> tuple : handlersCopy) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(response, variables);
						baseRequest.setHandled(true);
					}
				}
				_getHandlers.addAll(handlersCopy);
			} else if ("POST".equals(method)) {
				// Remove the handlers in case we need to add more in processing.
				List<HandlerTuple<IPostHandler>> handlersCopy = _postHandlers;
				_postHandlers = new ArrayList<>();
				
				for (HandlerTuple<IPostHandler> tuple : handlersCopy) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(response, variables, request.getParameterMap());
						baseRequest.setHandled(true);
					}
				}
				_postHandlers.addAll(handlersCopy);
			} else if ("PUT".equals(method)) {
				// Remove the handlers in case we need to add more in processing.
				List<HandlerTuple<IPutHandler>> handlersCopy = _putHandlers;
				_putHandlers = new ArrayList<>();
				
				for (HandlerTuple<IPutHandler> tuple : handlersCopy) {
					if (tuple.canHandle(method, target)) {
						String[] variables = tuple.parseVariables(target);
						tuple.handler.handle(response, variables, baseRequest.getInputStream());
						baseRequest.setHandled(true);
					}
				}
				_putHandlers.addAll(handlersCopy);
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
