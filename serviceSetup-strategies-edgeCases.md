# Flash Sale System: Operations, Concurrency, and Payment Reconciliation Guide

This document outlines the operational execution steps, the precise class and method locations for the concurrency mechanism, and the logic governing late-payment edge cases.

---

## 1. How to Run All Three Services

The entire microservice ecosystem is containerized and orchestrated via Docker Compose.

* **Prerequisites:** Docker and Docker Compose installed on your host machine.
* **Execution Command:** Navigate to the root project directory containing the `docker-compose.yml` file and run:
  `docker compose up --build -d`
* **Container Health Verification:** Run `docker compose ps` to verify that all databases and microservice instances are running.
* **Service Ports & Endpoints:**
    * **Inventory Service:** Runs on port `8081` (Health check via Spring Boot Actuator at `/actuator/health`).
    * **Reservation Service:** Runs on port `8082` (Health check via Spring Boot Actuator at `/actuator/health`).
    * **Order Service:** Runs on port `8083` (Health check via Spring Boot Actuator at `/actuator/health`).

---

## 2. Concurrency Strategy: Pessimistic Locking

To prevent race conditions, negative stock counts, and inventory overselling during massive traffic spikes, the system enforces database-level exclusive row locking.

* **Strategy:** Pessimistic Database Locking (`SELECT ... FOR UPDATE`) to serialize concurrent thread execution on high-demand SKUs without triggering optimistic locking retry storms.
* **Component Locations:**
    * **Repository Layer:** Located in the **`InventoryRepository`** interface within the `inventory-service`.
    * **Method Name:** **`findBySkuIdWithLock`**, annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to acquire an exclusive row-level lock on the targeted SKU record during the transaction.
    * **Service Layer:** Invoked inside the core stock allocation and decrement workflow within the **`InventoryService`** class, ensuring locks are released immediately upon transaction commit.

---

## 3. Late-Payment Behavior & Financial Reconciliation

When a payment gateway callback arrives after a user's reservation TTL has already expired and the inventory has been automatically returned to the active pool, the system executes a safe fallback protocol.

* **Strategy:** State verification followed by an automated compensating refund transaction to prevent inventory overselling and financial discrepancy.
* **Component Locations:**
    * **Service Layer:** Handled within the **`OrderService`** class inside the payment processing and callback handler method (such as **`handlePaymentCallback`**).
    * **External Client Interaction:** Utilizes the **`ReservationClient`** to query live reservation status and the **`PaymentAdapter`** method **`refundPayment`** to programmatically reverse unauthorized payments if the reservation is no longer active.
    * **Background Scheduler:** Managed by the **`ReservationExpiryScheduler`** component inside the `reservation-service` via its **`processExpiredReservations`** scheduled task, which automatically sweeps expired holds and triggers inventory release via the **`InventoryClient`** component.