package com.jeffdisher.breakwater;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public interface IPostHandler {
	void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException;
}
