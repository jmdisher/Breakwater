package com.jeffdisher.breakwater;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The interface defining an implementor of a POST REST invocation, where data was provided as "multipart/form-data".
 * NOTE:  Multi-part data can be large and must be read into memory before this invocation so users of this are limited
 * to 64 KiB per part and only 16 variables are allowed.
 * Given that this restriction is an assertion within the server, this should only be used in cases where the user is
 * absolutely certain that the parts are smaller than this.
 */
public interface IPostMultiPartHandler
{
	/**
	 * Handle the POST call, once the caller has identified the handler based on the path and parsed the multi-part
	 * data.
	 * 
	 * @param request The HTTP request (if any additional invocation data is required).
	 * @param response The HTTP response (where any response data must be written).
	 * @param path The path components of the invocation, exposed as the types created by the corresponding IPathParser
	 * @param multiPart The map of parts in the post (each part limited to 64 KiB).
	 * @throws IOException There was an IO error during invocation.
	 */
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<byte[]> multiPart) throws IOException;
}
