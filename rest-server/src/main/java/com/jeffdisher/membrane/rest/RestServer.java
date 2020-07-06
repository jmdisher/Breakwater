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
	private List<HandlerTuple> _handlers;

	public RestServer(int port) {
		_entryPoint = new EntryPoint();
		_server = new Server();
		ServerConnector connector = new ServerConnector(_server);
		connector.setPort(port);
		_server.setConnectors(new ServerConnector[] { connector });
		_server.setHandler(_entryPoint);
		_handlers = new ArrayList<>();
	}

	public void addHandler(String method, String pathPrefix, int variableCount, IHandler handler) {
		Assert.assertTrue(!pathPrefix.endsWith("/"));
		// Note that these should be sorted to avoid matching on a variable but we will just add later handlers to the front, since they tend to be more specific.
		_handlers.add(0, new HandlerTuple(method, pathPrefix, variableCount, handler));
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
			// Remove the handlers in case we need to add more in processing.
			List<HandlerTuple> handlersCopy = _handlers;
			_handlers = new ArrayList<>();
			
			for (HandlerTuple tuple : handlersCopy) {
				if (tuple.canHandle(method, target)) {
					String[] variables = tuple.parseVariables(target);
					tuple.handler.handle(response, variables, baseRequest.getInputStream());
					baseRequest.setHandled(true);
				}
			}
			_handlers.addAll(handlersCopy);
		}
	}


	private static class HandlerTuple {
		private final String _method;
		private final String _pathPrefix;
		private final int _variableCount;
		public final IHandler handler;
		
		public HandlerTuple(String method, String pathPrefix, int variableCount, IHandler handler) {
			_method = method;
			_pathPrefix = pathPrefix;
			_variableCount = variableCount;
			this.handler = handler;
		}
		
		public boolean canHandle(String method, String target) {
			return _method.equals(method) && target.startsWith(_pathPrefix) && ((target.substring(_pathPrefix.length()).split("/").length - 1) == _variableCount);
		}
		
		public String[] parseVariables(String target) {
			String variableString = target.substring(_pathPrefix.length());
			String[] variables = new String[_variableCount];
			System.arraycopy(variableString.split("/"), 1, variables, 0, _variableCount);
			return variables;
		}
	}
}
