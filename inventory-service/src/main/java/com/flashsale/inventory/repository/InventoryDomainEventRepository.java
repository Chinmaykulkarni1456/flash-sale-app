package com.flashsale.inventory.repository;

import com.flashsale.inventory.entity.InventoryDomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryDomainEventRepository extends JpaRepository<InventoryDomainEvent, String> {
    List<InventoryDomainEvent> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}