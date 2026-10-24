package com.billetterie.api_gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class GatewayRoutingTest {

    private static final HttpServer BACKEND = startBackend();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${local.server.port}")
    private int port;

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0
            );

            server.createContext("/events", exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                respond(exchange, 200, "events:" + query);
            });

            server.createContext("/users/me", exchange -> {
                String authorization = exchange.getRequestHeaders()
                        .getFirst("Authorization");

                if (authorization == null) {
                    respond(exchange, 401, "missing-token");
                } else {
                    respond(exchange, 200, "users:" + authorization);
                }
            });

            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot start HTTP test server", e
            );
        }
    }

    private static void respond(
            HttpExchange exchange, int status, String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set("Content-Type", "text/plain; charset=UTF-8");

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @DynamicPropertySource
    static void configureRoutes(DynamicPropertyRegistry registry) {
        String backendUrl = "http://127.0.0.1:"
                + BACKEND.getAddress().getPort();

        registry.add("app.routes.users-uri", () -> backendUrl);
        registry.add("app.routes.events-uri", () -> backendUrl);
    }

    private HttpResponse<String> get(
            String path, String authorization
    ) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET();

        if (authorization != null) {
            request.header("Authorization", authorization);
        }

        return client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void events_preservesPathAndQuery() throws Exception {
        HttpResponse<String> response = get(
                "/events?city=agadir", null
        );

        assertEquals(200, response.statusCode());
        assertEquals("events:city=agadir", response.body());
    }

    @Test
    void users_forwardsAuthorizationHeader() throws Exception {
        HttpResponse<String> response = get(
                "/users/me", "Bearer test-token"
        );

        assertEquals(200, response.statusCode());
        assertEquals("users:Bearer test-token", response.body());
    }

    @Test
    void users_preservesUnauthorizedResponse() throws Exception {
        HttpResponse<String> response = get("/users/me", null);

        assertEquals(401, response.statusCode());
        assertEquals("missing-token", response.body());
    }

    @Test
    void unknownRoute_returns404() throws Exception {
        HttpResponse<String> response = get(
                "/unknown-route", null
        );

        assertEquals(404, response.statusCode());
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }
}