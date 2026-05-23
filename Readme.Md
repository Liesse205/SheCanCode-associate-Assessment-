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

### System Flow Diagram

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant Storage
    
    Client->>Gateway: POST /process-payment
    Note right of Client: Header: Idempotency-Key: abc123
    
    Gateway->>Storage: Check if key exists
    Storage-->>Gateway: Key NOT found
    
    Gateway->>Gateway: Acquire lock for key
    Gateway->>Gateway: Process payment (2 sec)
    Gateway->>Storage: Store response
    
    Gateway-->>Client: 200 OK + Response

