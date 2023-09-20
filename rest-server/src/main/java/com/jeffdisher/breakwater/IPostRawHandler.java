package com.jeffdisher.breakwater;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The interface defining an implementor of a POST REST invocation, where the POST data MIME type wasn't a form or
 * multi-part.  This is the common case for large data uploads as they can be read from the request, directly.
 */
public interface IPostRawHandler
{
	/**
	 * Handle the POST call, once the caller has identified the handler based on the path.
	 * 
	 * @param request The HTTP request (if any additional invocation data is required).
	 * @param response The HTTP response (where any response data must be written).
	 * @param path The path components of the invocation, exposed as the types created by the corresponding IPathParser
	 * instances provided when registering the handler.
	 * @throws IOException There was an IO error during invocation.
	 */
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException;
}
