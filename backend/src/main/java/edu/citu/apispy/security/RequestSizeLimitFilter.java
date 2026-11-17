package edu.citu.apispy.security;

import edu.citu.apispy.web.InputLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rejects oversized /api/** request bodies (Content-Length and streamed reads).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	private final int maxBytes;

	public RequestSizeLimitFilter() {
		this(InputLimits.MAX_REQUEST_BYTES);
	}

	RequestSizeLimitFilter(int maxBytes) {
		this.maxBytes = Math.max(1, maxBytes);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		return !path.startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long contentLength = request.getContentLengthLong();
		if (contentLength > maxBytes) {
			writePayloadTooLarge(response);
			return;
		}

		filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
	}

	private static void writePayloadTooLarge(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
				"{\"error\":\"Request payload too large.\",\"message\":\"Request payload too large.\"}");
	}

	static final class LimitedRequest extends HttpServletRequestWrapper {
		private final int maxBytes;

		LimitedRequest(HttpServletRequest request, int maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return new LimitedServletInputStream(super.getInputStream(), maxBytes);
		}
	}

	static final class LimitedServletInputStream extends ServletInputStream {
		private final ServletInputStream delegate;
		private final int maxBytes;
		private int readTotal;
		private boolean overflow;

		LimitedServletInputStream(ServletInputStream delegate, int maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value >= 0) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = delegate.read(b, off, len);
			if (n > 0) {
				count(n);
			}
			return n;
		}

		private void count(int n) throws IOException {
			readTotal += n;
			if (readTotal > maxBytes) {
				overflow = true;
				throw new IOException("Request payload too large.");
			}
		}

		boolean isOverflow() {
			return overflow;
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			delegate.setReadListener(readListener);
		}
	}
}
