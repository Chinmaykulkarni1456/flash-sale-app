# Architecture Decision Record (ADR): Locking Strategy

* **Title:** ADR-001: Pessimistic Database Locking for High-Concurrency Inventory Allocation
* **Status:** Accepted & Implemented
* **Context:** Flash sale systems experience intense, sudden traffic spikes where thousands of concurrent requests contend for a limited inventory pool. Standard read-modify-write patterns introduce race conditions, leading to inventory overselling.

---

## 1. Context & Problem Statement
During a flash sale, multiple users concurrently attempt to reserve the same SKU. If `inventory-service` reads the available quantity (e.g., `1`), evaluates it in application memory, and then writes back the decremented value, concurrent threads will read `1` simultaneously. Both will proceed, resulting in negative inventory or overselling.

We required a locking and isolation strategy that guarantees strict consistency and prevents race conditions under high throughput without introducing unmanageable deadlock overhead or complex distributed consensus protocols.

## 2. Decision Drivers
* **Absolute Prevention of Overselling:** Under no circumstances can stock quantity drop below zero.
* **Low Latency:** High request volumes require fast transaction execution and minimal lock duration.
* **Simplicity & Maintainability:** Prefer robust, database-native primitives over complex external coordination mechanisms.

## 3. Considered Options
1. **Optimistic Locking (`@Version` column):** Rejected because extreme flash sale contention for a single popular SKU causes 99% of transactions to fail with `StaleObjectStateException`, introducing heavy retry storms.
2. **Distributed Locking (Redis/Redisson):** Rejected to avoid adding extra infrastructure complexity and split-brain risks.
3. **Pessimistic Database Locking (`SELECT ... FOR UPDATE`):** **Selected.** Guarantees serialization of concurrent operations on the exact inventory row. Threads wait in an orderly queue at the database level rather than failing and retrying.

## 4. Decision
We have implemented **Pessimistic Database Locking (`SELECT ... FOR UPDATE`)** inside strict transactional boundaries within the **`inventory-service`**.

When an allocation request arrives:
1. The `inventory-service` opens an ACID transaction.
2. It fetches the SKU row with an exclusive row-level lock (`FOR UPDATE`).
3. It validates whether `available_stock >= requested_quantity`.
4. If valid, it decrements the stock and commits the transaction immediately, releasing the lock.

## 5. Consequences & Mitigation
* **Positive:** Completely eliminates race conditions at the source of truth (`inventory_db`). Ensures data integrity and removes the retry loops common with optimistic locking.
* **Negative / Mitigation:** Potential database connection pool wait times under massive contention. *Mitigated* by keeping database transactions strictly scoped to allocation queries only (no external HTTP calls inside the transaction block) and utilizing short-lived TTL reservations in the `reservation-service` upstream.