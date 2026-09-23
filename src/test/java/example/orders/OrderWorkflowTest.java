package example.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderWorkflowTest {
    @Test void emailSuppressionHandsOffToSmsWithoutSendingEmail() {
        class RecordingGateway implements OrderWorkflow.Gateway {
            int users, emails, texts;
            public JsonNode createUser(OrderWorkflow.Order order) { users++; return JsonNodeFactory.instance.objectNode(); }
            public boolean emailSuppressed(String email) { return true; }
            public boolean smsSuppressed(String phone) { return false; }
            public String sendEmail(OrderWorkflow.Order order, String text) { emails++; return "email-1"; }
            public String sendSms(OrderWorkflow.Order order, String text) { texts++; return "sms-1"; }
        }
        RecordingGateway gateway = new RecordingGateway();
        var result = new OrderWorkflow(gateway).handle(new OrderWorkflow.Order("checkout-42", "42", "buyer@example.com", "+15550102030", "Ada", "email", "checkout"));
        assertEquals("sms", result.channel());
        assertEquals("sms-1", result.messageId());
        assertEquals(1, gateway.users);
        assertEquals(0, gateway.emails);
        assertEquals(1, gateway.texts);
    }
}
