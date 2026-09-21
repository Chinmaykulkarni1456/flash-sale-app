Late Payment & Reconciliation Strategy:
To handle the edge case where a payment provider successfully debits a user's account after the reservation TTL has expired and the stock has already been released, order-service evaluates the order state upon receiving the asynchronous payment callback. If the order is no longer in PENDING_PAYMENT status (i.e., it was expired by the reservation worker), the system treats the payment as a late successful payment, immediately executes an automated refund via the payment adapter, and sets the order status to EXPIRED_REFUNDED. This guarantees zero silent overselling and protects user funds.


All cases handled in this service

1. Order Creation & Validation Cases
   Happy Path Order Creation: Verifies that a given reservation ID is active (ACTIVE) via the ReservationClient, persists a new Order with PENDING_PAYMENT status, logs an initial PENDING payment attempt, and triggers the asynchronous payment workflow.

Invalid/Expired Reservation Rejection: Throws a BusinessException (INVALID_RESERVATION) and blocks order creation if the reservation is missing, expired, or no longer active.

2. Payment Callback & State Transition Cases
   Successful Payment Processing: Transitions the order to CONFIRMED, records a SUCCESS payment attempt with the transaction ID, confirms the reservation downstream, and commits inventory stock.

Failed Payment Processing: Transitions the order to PAYMENT_FAILED, records a FAILED payment attempt, cancels the reservation, and releases the inventory back to the pool.

Idempotent / Duplicate Callback Guard: Checks if an incoming callback or event targets an order already in a terminal state (anything other than PENDING_PAYMENT). If so, it safely ignores duplicate events and logs a warning.

3. Advanced Concurrency & Race Condition Cases
   Late Payment Success (Expired Reservation): Handles the edge case where a payment successfully processes at the gateway, but the reservation TTL expired in the meantime. Instead of committing an oversell, it intercepts the state, triggers an automatic refund (EXPIRED_REFUNDED), and logs a REFUNDED payment attempt.

Zombie Payment Prevention (Reconciliation Sweep): Prevents the background reconciliation worker from blindly failing stale orders. Before timing out an order stuck in PENDING_PAYMENT, it actively queries the payment gateway ledger. If a "zombie success" is discovered (gateway says success, but webhook was dropped), it routes the order back to the fulfillment/success pipeline rather than unjustly canceling the customer's purchase.