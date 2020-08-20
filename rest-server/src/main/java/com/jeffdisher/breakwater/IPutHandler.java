package com.jeffdisher.breakwater;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public interface IPutHandler {
	void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, InputStream inputStream) throws IOException;
}
