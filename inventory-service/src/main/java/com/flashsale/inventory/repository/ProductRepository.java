package com.flashsale.inventory.repository;

import com.flashsale.inventory.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    Optional<Product> findByTenantIdAndSku(String tenantId, String sku);
    Optional<Product> findByTenantIdAndId(String tenantId, String id);
}