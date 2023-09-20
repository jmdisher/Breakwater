package com.jeffdisher.breakwater;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The interface defining an implementor of a POST REST invocation, where data was provided as
 * "application/x-www-form-urlencoded".
 * NOTE:  Form data can be large and must be read into memory before this invocation so users of this are limited to 64
 * KiB per part and only 16 variables are allowed.
 */
public interface IPostFormHandler
{
	/**
	 * Handle the POST call, once the caller has identified the handler based on the path and parsed the form variables.
	 * 
	 * @param request The HTTP request (if any additional invocation data is required).
	 * @param response The HTTP response (where any response data must be written).
	 * @param path The path components of the invocation, exposed as the types created by the corresponding IPathParser
	 * @param formVariables The variables parsed from the "application/x-www-form-urlencoded" payload.
	 * @throws IOException There was an IO error during invocation.
	 */
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws IOException;
}
