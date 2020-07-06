package com.jeffdisher.membrane.rest;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;


public interface IPostHandler {
	void handle(HttpServletResponse response, String[] pathVariables, Map<String, String[]> postVariables) throws IOException;
}
