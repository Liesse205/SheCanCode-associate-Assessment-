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

    @PostMapping("/process-payment")
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

        // If this key was seen before, what's stored is returned
        if (storage.containsKey(key)) {
            StoredResponse stored = storage.get(key);

            // If amount or currency changed, the key is rejected
            if (!Objects.equals(stored.amount, request.getAmount()) ||
                    !Objects.equals(stored.currency, request.getCurrency().toUpperCase())) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Idempotency key already used for a different request body."));
            }
            // The X-Cache-Hit: Return the original response without processing again
            return ResponseEntity
                    .status(200)
                    .header("X-Cache-Hit", "true")
                    .body(Map.of(
                            "message", "Charged " + stored.amount + " " + stored.currency,
                            "amount", stored.amount,
                            "currency", stored.currency
                    ));
        }

        // Getting a lock specific to this key so other requests for same key will wait
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            System.out.println("Processing request for key: " + key + " at " + System.currentTimeMillis());
            // Double-check after acquiring lock
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
                // Simulation of real payment processing
                Thread.sleep(2000);

                // Saving the result so duplicates won't process again
                storage.put(key, new StoredResponse(
                        request.getAmount(),
                        request.getCurrency().toUpperCase()
                ));
                // Returning success to client
                return ResponseEntity.ok(Map.of(
                        "message", "Charged " + request.getAmount() + " " + request.getCurrency().toUpperCase(),
                        "amount", request.getAmount(),
                        "currency", request.getCurrency().toUpperCase()
                ));

            } catch (InterruptedException e) {
                // This happens if the server shuts down during processing and it restores the interrupt status
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
