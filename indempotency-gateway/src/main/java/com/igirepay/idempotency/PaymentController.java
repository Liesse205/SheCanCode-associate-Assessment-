package com.igirepay.idempotency;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
public class PaymentController {
    // Where idempotency keys and their results are stored
    private final ConcurrentHashMap<String, StoredResponse> storage = new ConcurrentHashMap<>();

    // We use a separate lock per key to avoid blocking different clients' requests
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    @PostMapping("/process-payment") // User Story 1: POST endpoint
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentRequest request) {

        // Input validation
        if (key == null || key.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Idempotency-Key header is required"));
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Valid amount is required"));
        }

        if (request.getCurrency() == null || request.getCurrency().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Currency is required"));
        }

        if (storage.containsKey(key)) {
            StoredResponse stored = storage.get(key);

            // User Story 3: If amount or currency changed, the key is rejected
            if (!Objects.equals(stored.amount, request.getAmount()) ||
                    !Objects.equals(stored.currency, request.getCurrency().toUpperCase())) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Idempotency key already used for a different request body."));
            }
            // User Story 2: Duplicate request returns cached response instantly
            return ResponseEntity
                    .status(200)
                    .header("X-Cache-Hit", "true")
                    .body(Map.of(
                            "message", "Charged " + stored.amount + " " + stored.currency,
                            "amount", stored.amount,
                            "currency", stored.currency
                    ));
        }

        // Bonus user story: In-Flight Check (Race Condition Handling)
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            System.out.println("Processing request for key: " + key + " at " + System.currentTimeMillis());
            // Double-check after acquiring lock(prevents race condition)
            if (storage.containsKey(key)) {
                StoredResponse stored = storage.get(key);
                return ResponseEntity
                        .status(200)
                        .header("X-Cache-Hit", "true")
                        .body(Map.of(
                                "message", "Charged " + stored.amount + " " + stored.currency,
                                "amount", stored.amount,
                                "currency", stored.currency
                        ));
            }

            try {
                // User Story 1: Simulation of payment processing with 2 second delay
                Thread.sleep(2000);

                // Saving the result so duplicates won't process again
                storage.put(key, new StoredResponse(
                        request.getAmount(),
                        request.getCurrency().toUpperCase()
                ));

                return ResponseEntity.ok(Map.of(
                        "message", "Charged " + request.getAmount() + " " + request.getCurrency().toUpperCase(),
                        "amount", request.getAmount(),
                        "currency", request.getCurrency().toUpperCase()
                ));

            } catch (InterruptedException e) {
                // Restore interrupt status if thread is interrupted
                Thread.currentThread().interrupt();
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Payment processing was interrupted"));

            } finally {
                keyLocks.remove(key);
            }
        }
    }

    static class StoredResponse {
        Integer amount;
        String currency;

        StoredResponse(Integer amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
    }
    // What the client sends us in the request body
    static class PaymentRequest {
        private Integer amount;
        private String currency;

        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}
