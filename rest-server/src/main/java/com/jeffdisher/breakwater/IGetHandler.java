package com.jeffdisher.breakwater;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public interface IGetHandler {
	void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException;
}
