package com.fmatrestaurant.menu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.fmatrestaurant.menu.domain.model.InventoryItem;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Real HTTP server shaped like the external Inventory contract ({@code GET /items/{id}}, answered with a flat
 * {@code {id, name, unit}} object), so the tests exercise the actual client of the backend.
 * It records every request and can be told to fail or to answer slowly. An article that does not exist is
 * answered like Inventory does: {@code 404} with the error envelope.
 */
public final class InventoryStub {

	/** One server for every suite; each test resets it. */
	public static final InventoryStub INSTANCE = new InventoryStub();

	/** A slow answer waits until the next {@link #reset()}, so it always exceeds this timeout. */
	private static final String TIMEOUT = "1s";

	private final HttpServer server;

	private final List<InventoryItem> items = new CopyOnWriteArrayList<>();

	private final List<String> requests = new CopyOnWriteArrayList<>();

	private volatile int failureStatus;

	private volatile String failureBody;

	/** Open (count 0) while requests are answered right away; {@link #answerSlowly()} closes it. */
	private volatile CountDownLatch slowAnswers = new CountDownLatch(0);

	private InventoryStub() {
		try {
			server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/items", this::handle);
		// A slow answer must not hold back the requests of the next test.
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
	}

	/** Points the Inventory client of the application to this server. */
	public static void register(DynamicPropertyRegistry registry) {
		registry.add("menu.inventory.base-url",
				INSTANCE::baseUrl);
		registry.add("menu.inventory.timeout", () -> TIMEOUT);
	}

	public String baseUrl() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	/** No articles, no recorded requests and normal answers. */
	public void reset() {
		items.clear();
		requests.clear();
		failureStatus = 0;
		// Also releases the slow answers still waiting, so they do not outlive their test.
		slowAnswers.countDown();
	}

	public InventoryItem add(String name, String unit) {
		return add(new InventoryItem("INV-" + UUID.randomUUID(), name, unit));
	}

	public InventoryItem add(InventoryItem item) {
		items.add(item);
		return item;
	}

	/** Every following request is answered with this status and body. */
	public void fail(int status, String body) {
		failureStatus = status;
		failureBody = body;
	}

	/** Every following request waits until the next {@link #reset()}, after the timeout of the client. */
	public void answerSlowly() {
		slowAnswers = new CountDownLatch(1);
	}

	/** Method, path and query of each request received, in order. */
	public List<String> requests() {
		return List.copyOf(requests);
	}

	public void clearRequests() {
		requests.clear();
	}

	private void handle(HttpExchange exchange) throws IOException {
		String query = exchange.getRequestURI().getRawQuery();
		requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
				+ (query == null ? "" : "?" + query));
		try {
			slowAnswers.await();
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
		}
		if (failureStatus != 0) {
			send(exchange, failureStatus, failureBody);
			return;
		}
		String itemPrefix = "/items/";
		if ("GET".equals(exchange.getRequestMethod())
				&& exchange.getRequestURI().getPath().startsWith(itemPrefix)) {
			String id = exchange.getRequestURI().getPath().substring(itemPrefix.length());
			InventoryItem item = items.stream().filter(candidate -> id.equals(candidate.id())).findFirst().orElse(null);
			if (item == null) {
				send(exchange, 404, "{\"error\": {\"message\": \"No se encontró el recurso solicitado.\"}}");
			} else {
				send(exchange, 200, "{\"id\": \"%s\", \"name\": \"%s\", \"unit\": \"%s\"}"
						.formatted(item.id(), item.name(), item.unit()));
			}
			return;
		}
		send(exchange, 501, "{\"error\": {\"message\": \"Endpoint not modeled\"}}");
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

}
