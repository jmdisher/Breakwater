package com.jeffdisher.membrane.rest;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;


/**
 * Basic client-side REST utility methods.
 */
public class RestHelpers {
	public static byte[] get(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		
		byte[] buffer;
		try {
			InputStream stream = connection.getInputStream();
			buffer = new byte[connection.getContentLength()];
			int didRead = 0;
			while (didRead < buffer.length) {
				int size = stream.read(buffer, didRead, buffer.length - didRead);
				didRead += size;
			}
		} catch (FileNotFoundException e) {
			// 404-ed!
			buffer = null;
		}
		return buffer;
	}

	public static byte[] put(String url, byte[] toSend) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("PUT");
		connection.setDoOutput(true);
		connection.getOutputStream().write(toSend);
		
		InputStream stream = connection.getInputStream();
		byte[] buffer = new byte[connection.getContentLength()];
		int didRead = 0;
		while (didRead < buffer.length) {
			int size = stream.read(buffer, didRead, buffer.length - didRead);
			didRead += size;
		}
		return buffer;
	}

	public static byte[] post(String url, Map<String, byte[]> toSend) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		// We need to send the variables as a multi-part POST, so we need a boundary and then we can start writing.
		String boundary = "===" + System.currentTimeMillis() + "===";
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		OutputStream outputStream = connection.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		for (Map.Entry<String, byte[]> entry : toSend.entrySet()) {
			_addPart(outputStream, writer, boundary, entry.getKey(), entry.getValue());
		}
		writer.append("--" + boundary + "--").append("\r\n");
		writer.close();
		InputStream stream = connection.getInputStream();
		byte[] buffer = new byte[connection.getContentLength()];
		int didRead = 0;
		while (didRead < buffer.length) {
			int size = stream.read(buffer, didRead, buffer.length - didRead);
			didRead += size;
		}
		return buffer;
	}

	public static byte[] delete(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("DELETE");
		
		InputStream stream = connection.getInputStream();
		byte[] buffer = new byte[connection.getContentLength()];
		int didRead = 0;
		while (didRead < buffer.length) {
			int size = stream.read(buffer, didRead, buffer.length - didRead);
			didRead += size;
		}
		return buffer;
	}


	private static void _addPart(OutputStream outputStream, BufferedWriter writer, String boundary, String key, byte[] value) throws IOException {
		String hyphens = "--";
		String lineFeed = "\r\n";
		writer.append(hyphens + boundary).append(lineFeed);
		writer.append("Content-Disposition: form-data; name=\"" + key + "\"").append(lineFeed);
		writer.append("Content-Type: application/octet-stream").append(lineFeed);
		writer.append(lineFeed);
		writer.flush();
		outputStream.write(value);
		outputStream.flush();
		writer.append(lineFeed);
	}
}
