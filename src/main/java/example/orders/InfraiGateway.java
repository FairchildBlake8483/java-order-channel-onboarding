package example.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class InfraiGateway implements OrderWorkflow.Gateway {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String key;

    public InfraiGateway(@Value("${infrai.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode call(String method, String path, Map<String, ?> body, String idempotencyKey) {
        try {
            String payload = body == null ? "" : json.writeValueAsString(body);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + key)
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
                if (body != null) request.header("Content-Type", "application/json");
                if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    String retry = response.headers().firstValue("Retry-After").orElse("");
                    long seconds;
                    try { seconds = Math.max(1, Long.parseLong(retry)); }
                    catch (NumberFormatException ignored) { seconds = 1L << attempt; }
                    Thread.sleep(Math.min(seconds, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiError(error.path("code").asText("UPSTREAM_ERROR"), error.toString(), response.statusCode());
                }
                return envelope.path("data");
            }
            throw new IllegalStateException("Retry budget exhausted");
        } catch (InfraiError e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (Exception e) { throw new IllegalStateException("Unable to complete request", e); }
    }

    public JsonNode createUser(OrderWorkflow.Order order) {
        return call("POST", "/v1/auth/user/create", Map.of("email", order.email(), "name", order.name(), "idempotency_key", order.eventId()), order.eventId());
    }

    public boolean emailSuppressed(String email) {
        return call("GET", "/v1/email/suppression/check/" + segment(email), null, null).path("suppressed").asBoolean(false);
    }

    public boolean smsSuppressed(String phone) {
        return call("POST", "/v1/sms/suppression/check", Map.of("phone", phone), null).path("suppressed").asBoolean(false);
    }

    public String sendEmail(OrderWorkflow.Order order, String text) {
        return call("POST", "/v1/email/send", Map.of("to", order.email(), "subject", "Order " + order.orderId(), "body", text), order.eventId() + ":email").path("message_id").asText();
    }

    public String sendSms(OrderWorkflow.Order order, String text) {
        return call("POST", "/v1/sms/send", Map.of("to", order.phone(), "body", text), order.eventId() + ":sms").path("message_id").asText();
    }

    public static class InfraiError extends RuntimeException {
        private final int status;
        public InfraiError(String code, String detail, int status) { super(code + ": " + detail); this.status = status; }
        public int status() { return status; }
    }
}
