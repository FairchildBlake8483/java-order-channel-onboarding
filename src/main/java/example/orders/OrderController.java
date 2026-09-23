package example.orders;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class OrderController {
    private final OrderWorkflow workflow;
    public OrderController(OrderWorkflow workflow) { this.workflow = workflow; }

    @PostMapping("/orders/events")
    public OrderWorkflow.Decision accept(@RequestBody OrderWorkflow.Order order) { return workflow.handle(order); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(InfraiGateway.InfraiError.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiGateway.InfraiError error) {
        HttpStatus status = error.status() >= 400 && error.status() < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", error.getMessage()));
    }
}
