package com.fmatrestaurant.menu.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fmatrestaurant.menu.api.ApiExceptionHandler.ErrorBody;
import com.fmatrestaurant.menu.api.ApiExceptionHandler.ErrorEnvelope;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.infrastructure.IdempotencyRecord;
import com.fmatrestaurant.menu.infrastructure.IdempotencyRecordRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotency-Key of the operations that create a resource, and of PATCH of offers. A retry of a request
 * that already succeeded gets its original status, body and ETag back, before resources or the current
 * If-Match are checked again, without running the operation a second time.
 *
 * <p>The operation runs in the transaction that stores its response, so either both are committed or
 * neither is: there is never an effect without a response to replay. That transaction holds an advisory
 * lock on the key, so a concurrent request with the same key gets 409 instead of running it again.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

	static final String HEADER = "Idempotency-Key";

	// ponytail: there is no authentication yet, so every request shares one subject of the only menu. Replace
	// both with the validated identity and its menu once the trusted layer exists, never with client data.
	private static final String SUBJECT = "anonymous";

	private static final Pattern KEY = Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");

	private static final List<Route> ROUTES = List.of(
			new Route("POST", "/api/v1/menu/categories"),
			new Route("POST", "/api/v1/menu/entries"),
			new Route("POST", "/api/v1/menu/entries/[^/]+/offers"),
			new Route("POST", "/api/v1/menu/recipes"),
			new Route("POST", "/api/v1/media/images"),
			new Route("PATCH", "/api/v1/menu/offers/[^/]+"));

	/** Property order and whitespace do not matter; array order, null and omitted properties do. */
	private static final JsonMapper CANONICAL = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED)
			.build();

	private static final JsonMapper ERRORS = JsonMapper.builder().build();

	private final IdempotencyRecordRepository records;

	private final TransactionTemplate transactions;

	public IdempotencyFilter(IdempotencyRecordRepository records, PlatformTransactionManager transactionManager) {
		this.records = records;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = path(request);
		return ROUTES.stream().noneMatch(route -> route.matches(request.getMethod(), path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		if (key == null || !KEY.matcher(key).matches()) {
			error(response, HttpStatus.BAD_REQUEST, "Send Idempotency-Key with a valid UUID.", "INVALID_IDEMPOTENCY_KEY");
			return;
		}
		boolean patch = "PATCH".equals(request.getMethod());
		String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
		if (patch && ifMatch == null) {
			error(response, HttpStatus.PRECONDITION_REQUIRED, "The If-Match header is required.", null);
			return;
		}
		HttpServletRequest replayable = multipart(request) ? request : new CachedBodyRequest(request);
		String fingerprint = fingerprint(replayable, patch ? ifMatch : null);
		String id = hex(digest().digest(String.join("\n", SUBJECT, String.valueOf(CategoryService.DEFAULT_MENU_ID),
				request.getMethod(), path(request), key.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));
		ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
		Outcome outcome = execute(id, fingerprint, chain, replayable, captured);
		respond(outcome, id, fingerprint, captured);
	}

	/** Runs the operation, or decides not to, in the transaction that stores its response. */
	private Outcome execute(String id, String fingerprint, FilterChain chain, HttpServletRequest request,
			ContentCachingResponseWrapper captured) throws ServletException, IOException {
		try {
			return transactions.execute(transaction -> attempt(transaction, id, fingerprint, chain, request, captured));
		}
		catch (ChainException e) {
			if (e.getCause() instanceof IOException io) {
				throw io;
			}
			throw (ServletException) e.getCause();
		}
		catch (RuntimeException e) {
			// The commit failed, so the captured response must not reach the client.
			if (!captured.getResponse().isCommitted()) {
				captured.getResponse().reset();
			}
			throw e;
		}
	}

	private Outcome attempt(TransactionStatus transaction, String id, String fingerprint, FilterChain chain,
			HttpServletRequest request, ContentCachingResponseWrapper captured) {
		if (!records.tryLock(id)) {
			return new Outcome(State.IN_PROGRESS, null);
		}
		IdempotencyRecord current = records.findById(id).filter(found -> !found.expired(Instant.now())).orElse(null);
		if (current != null && !current.getFingerprint().equals(fingerprint)) {
			return new Outcome(State.REUSED, null);
		}
		if (current != null && current.completed()) {
			return new Outcome(State.REPLAYED, current);
		}
		run(chain, request, captured);
		if (captured.getStatus() / 100 == 2) {
			records.save(new IdempotencyRecord(id, fingerprint, Instant.now()).complete(captured.getStatus(),
					captured.getContentType(), captured.getHeader(HttpHeaders.ETAG), captured.getContentAsByteArray(),
					Instant.now()));
			return new Outcome(State.EXECUTED, null);
		}
		transaction.setRollbackOnly();
		return new Outcome(State.FAILED, null);
	}

	/** Sends the answer only after the commit, so the client never sees a result that was not stored. */
	private void respond(Outcome outcome, String id, String fingerprint, ContentCachingResponseWrapper captured)
			throws IOException {
		HttpServletResponse response = (HttpServletResponse) captured.getResponse();
		switch (outcome.state()) {
			case IN_PROGRESS -> {
				response.setHeader(HttpHeaders.RETRY_AFTER, "1");
				error(response, HttpStatus.CONFLICT, "The request with this idempotency key is still in progress.",
						"IDEMPOTENCY_IN_PROGRESS");
			}
			case REUSED -> error(response, HttpStatus.CONFLICT,
					"The idempotency key is already bound to a different request.", "IDEMPOTENCY_KEY_REUSED");
			case REPLAYED -> replay(response, outcome.stored());
			case FAILED -> {
				bind(id, fingerprint);
				captured.copyBodyToResponse();
			}
			default -> captured.copyBodyToResponse();
		}
	}

	/**
	 * Nothing was changed, so the same request may run again; the key stays bound to it, so a different
	 * request with this key is rejected.
	 */
	private void bind(String id, String fingerprint) {
		transactions.executeWithoutResult(transaction -> {
			records.lock(id);
			if (records.findById(id).filter(found -> !found.expired(Instant.now())).isEmpty()) {
				records.save(new IdempotencyRecord(id, fingerprint, Instant.now()));
			}
		});
	}

	private static void run(FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
		try {
			chain.doFilter(request, response);
		}
		catch (IOException | ServletException e) {
			throw new ChainException(e);
		}
	}

	private static void replay(HttpServletResponse response, IdempotencyRecord stored) throws IOException {
		response.setStatus(stored.getResponseStatus());
		if (stored.getResponseContentType() != null) {
			response.setContentType(stored.getResponseContentType());
		}
		if (stored.getResponseEtag() != null) {
			response.setHeader(HttpHeaders.ETAG, stored.getResponseEtag());
		}
		byte[] body = stored.getResponseBody() == null ? new byte[0] : stored.getResponseBody();
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
	}

	private static void error(HttpServletResponse response, HttpStatus status, String message, String code)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ERRORS.writeValue(response.getOutputStream(),
				new ErrorEnvelope(new ErrorBody(message, code == null ? null : Map.of("code", code))));
	}

	/**
	 * JSON in canonical form, or its bytes if it is not JSON; an image by the declared media type and the
	 * bytes of its {@code file} part, whatever the boundary, file name or order of the parts. In PATCH, the
	 * original If-Match too.
	 */
	private static String fingerprint(HttpServletRequest request, String ifMatch) throws IOException {
		MessageDigest digest = digest();
		if (ifMatch != null) {
			digest.update(ifMatch.getBytes(StandardCharsets.UTF_8));
		}
		digest.update((byte) 0);
		if (request instanceof CachedBodyRequest cached) {
			digest.update(canonical(cached.body));
		}
		else {
			Part file = filePart(request);
			if (file != null) {
				digest.update(String.valueOf(file.getContentType()).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
				try (InputStream content = file.getInputStream()) {
					digest.update(content.readAllBytes());
				}
			}
		}
		return hex(digest.digest());
	}

	private static byte[] canonical(byte[] body) {
		try {
			JsonNode tree = CANONICAL.readTree(body);
			if (tree != null && !tree.isMissingNode()) {
				return CANONICAL.writeValueAsBytes(tree);
			}
		}
		catch (JacksonException _) {
			// Not JSON: the operation rejects it, and its bytes identify it.
		}
		return body;
	}

	/** Null if there is none or the multipart cannot be read; the operation then reports why. */
	private static Part filePart(HttpServletRequest request) {
		try {
			return request.getPart("file");
		}
		catch (IOException | ServletException | IllegalStateException _) {
			return null;
		}
	}

	private static boolean multipart(HttpServletRequest request) {
		String type = request.getContentType();
		return type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/");
	}

	private static String path(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] bytes) {
		return HexFormat.of().formatHex(bytes);
	}

	private enum State {
		IN_PROGRESS, REUSED, REPLAYED, EXECUTED, FAILED
	}

	private record Outcome(State state, IdempotencyRecord stored) {
	}

	private record Route(String method, Pattern path) {

		Route(String method, String path) {
			this(method, Pattern.compile(path));
		}

		boolean matches(String requestMethod, String requestPath) {
			return method.equals(requestMethod) && path.matcher(requestPath).matches();
		}

	}

	/** Carries the checked exceptions of the filter chain out of the transaction callback. */
	private static final class ChainException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		ChainException(Exception cause) {
			super(cause);
		}

	}

	/** The body was read for the fingerprint, so the operation reads it again from memory. */
	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request) throws IOException {
			super(request);
			this.body = request.getInputStream().readAllBytes();
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
			Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

	}

}
