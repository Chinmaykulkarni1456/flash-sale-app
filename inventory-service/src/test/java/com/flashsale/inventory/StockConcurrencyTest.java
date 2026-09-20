package com.flashsale.inventory;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.inventory.dto.StockOperationRequest;
import com.flashsale.inventory.entity.Product;
import com.flashsale.inventory.entity.WarehouseStock;
import com.flashsale.inventory.repository.ProductRepository;
import com.flashsale.inventory.repository.WarehouseStockRepository;
import com.flashsale.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class StockConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseStockRepository stockRepository;

    private final String tenantId = "tenant-alpha";
    private final String productId = "prod-flash-1";
    private final String warehouseId = "wh-main";

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();
        productRepository.deleteAll();

        Product product = Product.builder()
                .id(productId)
                .tenantId(tenantId)
                .sku("SKU-FLASH-1")
                .name("Flash Sale Phone")
                .build();
        productRepository.save(product);

        WarehouseStock stock = WarehouseStock.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .productId(productId)
                .warehouseId(warehouseId)
                .onHand(10)
                .reserved(0)
                .build();
        stockRepository.save(stock);
    }

    @Test
    void testConcurrentAllocations_ShouldNeverOversell() throws Exception {
        int totalRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    TenantContext.setTenantId(tenantId);

                    StockOperationRequest req = new StockOperationRequest();
                    req.setProductId(productId);
                    req.setWarehouseId(warehouseId);
                    req.setQuantity(1);

                    inventoryService.allocateStock(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(10, successCount.get(), "Exactly 10 allocations should succeed");
        assertEquals(90, failureCount.get(), "90 allocations should fail with INSUFFICIENT_STOCK");

        WarehouseStock finalStock = stockRepository.findByTenantIdAndProductId(tenantId, productId).get(0);
        assertEquals(10, finalStock.getOnHand(), "On hand stock remains 10 until committed");
        assertEquals(10, finalStock.getReserved(), "Reserved quantity must equal total initial stock");
        assertEquals(0, finalStock.getAvailable(), "Available stock must be 0");
    }
}