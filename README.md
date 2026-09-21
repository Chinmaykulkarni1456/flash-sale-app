# flash-sale-app
Multi-service flash sale reservation &amp; order fulfillment engine with zero-oversell stock concurrency controls.


here is the complete End-to-End User Journey Flow for the flash sale platform:

Step 1: Admin Inventory Setup (inventory-service)
Action: An Admin user (authenticated via JWT with the ADMIN role) calls the admin APIs to set up products (SKUs) and seed physical stock into warehouses.

Behavior: The inventory-service tracks stock metrics (on_hand, reserved, available) ensuring initial stock is properly recorded under the specific tenant context.

Step 2: Flash Sale Reservation (reservation-service)
Action: A Customer (USER role) wants to buy a flash sale item. They send a request to POST /api/v1/reservations with the SKU, desired quantity, a unique Idempotency-Key header, and their JWT.

Idempotency Check: The reservation-service checks if this Idempotency-Key was already processed for the tenant. If it's a network retry, it safely replays the existing active reservation without double-booking.

Stock Allocation: The reservation-service makes a synchronous REST call to inventory-service (/api/v1/internal/stock/allocate).

Concurrency Guard: The inventory-service applies an atomic database update/lock to guarantee that concurrent users cannot oversell the available stock.

Hold Creation: Once allocated successfully, a reservation hold is saved with a 10-minute TTL (Time-To-Live) and a status of ACTIVE.

Step 3: Order Conversion (order-service)
Action: Within the 10-minute window, the user proceeds to checkout by calling POST /api/v1/orders referencing their valid reservation ID.

Validation: The order-service ensures the reservation exists, belongs to the correct tenant, and has not yet expired. A unique constraint ensures a reservation can only ever be converted into one order.

Step 4: Asynchronous Payment & Fulfillment
Payment Trigger: Upon order creation, order-service invokes the Fake Payment Adapter asynchronously via a background thread pool (non-blocking for the HTTP client).

Outcome Branches:

Scenario A (Payment Success): The reservation is confirmed, and inventory stock is permanently committed (deducted from on_hand). The order status becomes CONFIRMED.

Scenario B (Payment Failure/Timeout): The reservation is cancelled, and inventory stock is released back to the available pool. The order status becomes FAILED.

Step 5: Background Expiry & Late Payment Edge Case
TTL Background Worker: If the user abandons checkout, a scheduled background worker in reservation-service runs every 10 seconds to catch active holds where expires_at < NOW(). It automatically marks them EXPIRED and notifies inventory-service to release the stock.

Late Payment Protection: If a delayed payment callback arrives after a reservation has already expired, the system rejects overselling by checking inventory availability. If stock is no longer available, it triggers an automatic refund compensation flow.


****Trade offs****

1. Latency & Network Overhead vs. Strict Correctness
   (The trade-off behind the Expiry vs. Payment Check)

What you gave up: Speed and performance.
The cost: By forcing the webhook handler or timeout worker to make extra synchronous HTTP calls across service boundaries (re-checking the reservation service or calling paymentAdapter.queryGatewayStatus()), you add network latency and increase load on downstream services.
The bigger picture: You chose to sacrifice a few milliseconds of response time to completely eliminate the risk of race conditions, ensuring you never resurrect dead inventory or keep a zombie payment.

2. Write Throughput & DB Load vs. Absolute Durability
   (The trade-off behind persistent idempotency and payment attempts)

What you gave up: Raw throughput.
The cost: Writing every payment attempt, status change, and idempotency key to a relational database (paymentAttemptRepository) introduces disk I/O, database locks, and storage overhead. An in-memory cache (like Redis) could handle duplicate checks magnitudes faster.
The bigger picture: You traded peak transaction speed for ACID durability. If a pod crashes mid-flash-sale, your database-backed ledger ensures that idempotency state is never lost, preventing double-processing even after a hard restart.

3. Eventual Consistency Simplicity vs. Distributed Coordination Overhead
   (The trade-off of your overall architecture)

What you gave up: Instant, rigid consistency.
The cost: Instead of using heavy, blocking distributed transactions (like Two-Phase Commit / 2PC) to lock the order, reservation, and inventory simultaneously across services, you used independent state machines, asynchronous callbacks, and reconciliation jobs (timeoutOrder). This means you have to write complex compensation logic (like automatic refunds and stock release).
The bigger picture: You avoided a tight coupling that would cripple your system's availability and scaling limits under flash-sale traffic spikes, choosing instead a resilient asynchronous model that gracefully handles failures.