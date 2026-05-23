# Idempotency Gateway - Payment Processing System

A production-ready idempotency layer that prevents double charging in payment systems. Built for IgirePay Technologies as part of the SheCanCODE Java Backend Associate Program.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Setup Instructions](#setup-instructions)
- [API Documentation](#api-documentation)
- [Design Decisions](#design-decisions)
- [Developer's Choice Feature](#developers-choice-feature)
- [Test Results](#test-results)
- [Tech Stack](#tech-stack)

---

## Architecture Overview

### Flowchart
```mermaid
sequenceDiagram
    participant C as Client
    participant G as Idempotency Gateway
    participant S as Storage (ConcurrentHashMap)
    participant L as Lock Manager
    
    Note over C,L: FIRST REQUEST (New Key)
    
    C->>G: POST /process-payment
    Note right of C: Header: Idempotency-Key: ABC123
    Note right of C: Body: {amount:100, currency:RWF}
    
    G->>G: Validate Input
    G->>S: Contains key ABC123?
    S-->>G: false (Not found)
    
    G->>L: Acquire lock for ABC123
    activate L
    
    G->>S: Double-check key exists?
    S-->>G: false
    
    G->>G: Process Payment (Thread.sleep 2000ms)
    Note over G: Simulating bank API call
    
    G->>S: Store response with key ABC123
    G->>L: Release lock (finally block)
    deactivate L
    
    G-->>C: 200 OK {message: "Charged 100 RWF"}
    
    Note over C,L: DUPLICATE REQUEST (Same Key)
    
    C->>G: POST /process-payment
    Note right of C: Header: Idempotency-Key: ABC123
    Note right of C: Body: {amount:100, currency:RWF}
    
    G->>S: Contains key ABC123?
    S-->>G: true (Found)
    
    G->>G: Compare request body with stored
    Note over G: Objects.equals() for null safety
    
    G-->>C: 200 OK + X-Cache-Hit: true
    Note right of G: Instant response, no delay
    
    Note over C,L: CONFLICT CASE (Same Key, Different Body)
    
    C->>G: POST /process-payment
    Note right of C: Header: Idempotency-Key: ABC123
    Note right of C: Body: {amount:500, currency:RWF}
    
    G->>S: Contains key ABC123?
    S-->>G: true (Found)
    
    G->>G: Compare body - DIFFERENT amount!
    
    G-->>C: 409 CONFLICT
    Note right of G: "Idempotency key already used for different request body"


