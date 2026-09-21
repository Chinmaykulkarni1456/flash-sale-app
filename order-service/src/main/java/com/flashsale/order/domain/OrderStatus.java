package com.flashsale.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    EXPIRED_REFUNDED,
    FAILED_REFUNDED
}