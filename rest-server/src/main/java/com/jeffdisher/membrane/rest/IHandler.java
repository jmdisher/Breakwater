package com.jeffdisher.membrane.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;


public interface IHandler {
	void handle(HttpServletResponse response, String[] variables, String inputLine) throws IOException;
}
