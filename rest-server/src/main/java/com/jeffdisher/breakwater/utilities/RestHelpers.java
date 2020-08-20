package com.jeffdisher.breakwater.utilities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import com.jeffdisher.breakwater.StringMultiMap;


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
		
		return _readResponse(connection);
	}

	public static byte[] postParts(String url, StringMultiMap<byte[]> toSend) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		// We need to send the variables as a multi-part POST, so we need a boundary and then we can start writing.
		String boundary = "===" + System.currentTimeMillis() + "===";
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		OutputStream outputStream = connection.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		for (Map.Entry<String, List<byte[]>> entry : toSend.entrySet()) {
			String key = entry.getKey();
			for (byte[] value : entry.getValue()) {
				_addPart(outputStream, writer, boundary, key, value);
			}
		}
		writer.append("--" + boundary + "--").append("\r\n");
		writer.close();
		return _readResponse(connection);
	}

	public static byte[] postForm(String url, StringMultiMap<String> toSend) throws MalformedURLException, IOException {
		// Assemble the form.
		boolean appendAmpersand = false;
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, List<String>> entry : toSend.entrySet()) {
			String key = entry.getKey();
			for (String value : entry.getValue()) {
				if (appendAmpersand) {
					builder.append("&");
				}
				appendAmpersand = true;
				builder.append(URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"));
			}
		}
		String serializedForm = builder.toString();
		
		// Write the form.
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
		connection.setRequestProperty("Content-Length", Integer.toString(serializedForm.length()));
		OutputStream outputStream = connection.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		writer.write(serializedForm);
		writer.close();
		
		return _readResponse(connection);
	}

	public static byte[] postBinary(String url, byte[] raw) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/octet-stream");
		connection.setRequestProperty("Content-Length", Integer.toString(raw.length));
		OutputStream outputStream = connection.getOutputStream();
		outputStream.write(raw);
		outputStream.close();
		
		return _readResponse(connection);
	}

	public static byte[] delete(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setRequestMethod("DELETE");
		
		return _readResponse(connection);
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

	private static byte[] _readResponse(HttpURLConnection connection) throws IOException {
		// Read the response.
		InputStream stream = connection.getInputStream();
		byte[] buffer = new byte[connection.getContentLength()];
		int didRead = 0;
		while (didRead < buffer.length) {
			int size = stream.read(buffer, didRead, buffer.length - didRead);
			didRead += size;
		}
		return buffer;
	}
}
