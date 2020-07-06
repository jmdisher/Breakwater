package com.jeffdisher.membrane.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;


public interface IDeleteHandler {
	void handle(HttpServletResponse response, String[] pathVariables) throws IOException;
}
