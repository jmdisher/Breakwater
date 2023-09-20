package com.jeffdisher.breakwater;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The interface defining an implementor of a PUT REST invocation.  Much like the raw POST type, this is another method
 * which is appropriate for uploading large streams of raw data.
 */
public interface IPutHandler
{
	/**
	 * Handle the PUT call, once the caller has identified the handler based on the path and prepared the inputStream
	 * so the implementation can read the remaining PUT data.
	 * 
	 * @param request The HTTP request (if any additional invocation data is required).
	 * @param response The HTTP response (where any response data must be written).
	 * @param path The path components of the invocation, exposed as the types created by the corresponding IPathParser
	 * instances provided when registering the handler.
	 * @param inputStream The stream of raw data uploaded with the PUT call.
	 * @throws IOException There was an IO error during invocation.
	 */
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, InputStream inputStream) throws IOException;
}
