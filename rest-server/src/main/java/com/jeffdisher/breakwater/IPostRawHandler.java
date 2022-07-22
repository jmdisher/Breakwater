package com.jeffdisher.breakwater;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public interface IPostRawHandler {
	void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, byte[] rawPost) throws IOException;
}
