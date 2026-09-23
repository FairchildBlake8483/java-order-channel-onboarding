package example.orders;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class OrderWorkflow {
    public record Order(String eventId, String orderId, String email, String phone, String name,
                        String signupChannel, String stage) {}
    public record Decision(String orderId, String stage, String channel, String status, String messageId) {}

    public interface Gateway {
        JsonNode createUser(Order order);
        boolean emailSuppressed(String email);
        boolean smsSuppressed(String phone);
        String sendEmail(Order order, String text);
        String sendSms(Order order, String text);
    }

    private final Gateway gateway;
    public OrderWorkflow(Gateway gateway) { this.gateway = gateway; }

    public Decision handle(Order order) {
        if (order.eventId() == null || order.eventId().isBlank() || order.orderId() == null || order.orderId().isBlank()
            || order.email() == null || order.email().isBlank() || order.name() == null || order.name().isBlank()
            || order.signupChannel() == null || !java.util.List.of("email", "sms").contains(order.signupChannel())
            || order.stage() == null || !java.util.List.of("checkout", "fulfillment", "receipt", "order_update").contains(order.stage()))
            throw new IllegalArgumentException("Valid eventId, orderId, email, name, signupChannel and stage are required");
        if (order.stage().equals("checkout")) gateway.createUser(order);
        String text = switch (order.stage()) {
            case "checkout" -> "Welcome, " + order.name() + ". Order " + order.orderId() + " is confirmed.";
            case "fulfillment" -> "Order " + order.orderId() + " is being fulfilled.";
            case "receipt" -> "Receipt for order " + order.orderId() + " is ready.";
            default -> "Order " + order.orderId() + " has an update.";
        };
        boolean preferSms = order.signupChannel().equals("sms");
        if (!preferSms && !gateway.emailSuppressed(order.email()))
            return new Decision(order.orderId(), order.stage(), "email", "sent", gateway.sendEmail(order, text));
        if (order.phone() != null && !order.phone().isBlank() && !gateway.smsSuppressed(order.phone()))
            return new Decision(order.orderId(), order.stage(), "sms", "sent", gateway.sendSms(order, text));
        if (preferSms && !gateway.emailSuppressed(order.email()))
            return new Decision(order.orderId(), order.stage(), "email", "sent", gateway.sendEmail(order, text));
        return new Decision(order.orderId(), order.stage(), "none", "suppressed", null);
    }
}
