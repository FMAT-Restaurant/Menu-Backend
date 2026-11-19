package com.fmatrestaurant.menu.api.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Keeps the body of a request so authorization can read it and the next filters can read it again. */
public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

	private final byte[] body;

	public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
		super(request);
		this.body = request.getInputStream().readAllBytes();
	}

	public byte[] body() {
		return body.clone();
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream content = new ByteArrayInputStream(body);
		return new ServletInputStream() {

			@Override
			public int read() {
				return content.read();
			}

			@Override
			public int read(byte[] buffer, int offset, int length) {
				return content.read(buffer, offset, length);
			}

			@Override
			public boolean isFinished() {
				return content.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener listener) {
				throw new UnsupportedOperationException("The body is already in memory");
			}

		};
	}

	@Override
	public BufferedReader getReader() {
		String encoding = getCharacterEncoding();
		return new BufferedReader(new InputStreamReader(getInputStream(),
				encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
	}

	@Override
	public int getContentLength() {
		return body.length;
	}

	@Override
	public long getContentLengthLong() {
		return body.length;
	}

}
