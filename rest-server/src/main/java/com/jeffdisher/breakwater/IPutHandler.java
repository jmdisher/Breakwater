package com.jeffdisher.breakwater;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public interface IPutHandler
{
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, InputStream inputStream) throws IOException;
}
