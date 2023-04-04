package com.jeffdisher.breakwater;

import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;


/**
 * A factory provided to handle web socket connections.  A factory is needed instead of just a handler (like normal HTTP
 * methods) since the web socket is a long-lived bidirectional connection.
 */
public interface IWebSocketFactory {
	/**
	 * Creates a Jetty WebSocketListener with the given path variables.  The variable array will be as long as the
	 * number of variables requested when registering the factory.
	 * 
	 * @param upgradeRequest The actual HTTP protocol upgrade request.
	 * @param variables The variables from the requested path of the web socket.
	 * @return A Jetty WebSocketListener, or null if the upgrade request should be rejected.
	 */
	WebSocketListener create(JettyServerUpgradeRequest upgradeRequest, String[] variables);
}
