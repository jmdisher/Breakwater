package com.jeffdisher.breakwater;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public interface IGetHandler
{
	void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException;
}
