package com.jeffdisher.membrane.rest;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletResponse;


public interface IHandler {
	void handle(HttpServletResponse response, String[] pathVariables, InputStream inputStream) throws IOException;
}
