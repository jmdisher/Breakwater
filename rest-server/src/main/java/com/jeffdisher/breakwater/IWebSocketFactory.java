package com.jeffdisher.breakwater;

import org.eclipse.jetty.websocket.api.WebSocketListener;


/**
 * A factory provided to handle web socket connections.  A factory is needed instead of just a handler (like normal HTTP
 * methods) since the web socket is a long-lived bidirectional connection.
 */
public interface IWebSocketFactory {
	/**
	 * Creates a Jetty WebSocketListener with the given path variables.  The variable array will be as long as the
	 * number of variables requested when registering the factory.
	 * 
	 * @param variables The variables from the requested path of the web socket.
	 * @return A Jetty WebSocketListener.
	 */
	WebSocketListener create(String[] variables);
}
