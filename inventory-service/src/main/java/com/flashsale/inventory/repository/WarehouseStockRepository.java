package com.flashsale.inventory.repository;

import com.flashsale.inventory.entity.WarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, String> {

    List<WarehouseStock> findByTenantIdAndProductId(String tenantId, String productId);

    Optional<WarehouseStock> findByTenantIdAndProductIdAndWarehouseId(String tenantId, String productId, String warehouseId);

    @Modifying
    @Query("""
        UPDATE WarehouseStock w
        SET w.reserved = w.reserved + :quantity,
            w.updatedAt = CURRENT_INSTANT
        WHERE w.tenantId = :tenantId
          AND w.productId = :productId
          AND w.warehouseId = :warehouseId
          AND (w.onHand - w.reserved) >= :quantity
    """)
    int allocateStockAtomic(
            @Param("tenantId") String tenantId,
            @Param("productId") String productId,
            @Param("warehouseId") String warehouseId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query("""
        UPDATE WarehouseStock w
        SET w.reserved = w.reserved - :quantity,
            w.updatedAt = CURRENT_INSTANT
        WHERE w.tenantId = :tenantId
          AND w.productId = :productId
          AND w.warehouseId = :warehouseId
          AND w.reserved >= :quantity
    """)
    int releaseStockAtomic(
            @Param("tenantId") String tenantId,
            @Param("productId") String productId,
            @Param("warehouseId") String warehouseId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query("""
        UPDATE WarehouseStock w
        SET w.onHand = w.onHand - :quantity,
            w.reserved = w.reserved - :quantity,
            w.updatedAt = CURRENT_INSTANT
        WHERE w.tenantId = :tenantId
          AND w.productId = :productId
          AND w.warehouseId = :warehouseId
          AND w.reserved >= :quantity
          AND w.onHand >= :quantity
    """)
    int commitStockAtomic(
            @Param("tenantId") String tenantId,
            @Param("productId") String productId,
            @Param("warehouseId") String warehouseId,
            @Param("quantity") int quantity
    );
}