/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's prepared batch statement subsystem for a warehouse restock.
public class WarehouseRestockBatchTest {
    @Test
    void recordsAStockReplenishmentBatch() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:warehouse_restock");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE stock (
                        sku VARCHAR(32) PRIMARY KEY,
                        quantity INT NOT NULL
                    )
                    """);

            PreparedBatch batch = handle.prepareBatch("""
                    INSERT INTO stock (sku, quantity)
                    VALUES (:sku, :quantity)
                    """);
            int[] results = batch
                    .bind("sku", "KB-01")
                    .bind("quantity", 18)
                    .add()
                    .bind("sku", "MS-02")
                    .bind("quantity", 7)
                    .add()
                    .execute();

            assertArrayEquals(new int[] {1, 1}, results);
            assertEquals(List.of(
                    Map.of("sku", "KB-01", "quantity", 18),
                    Map.of("sku", "MS-02", "quantity", 7)),
                    handle.createQuery("""
                            SELECT sku, quantity
                            FROM stock
                            ORDER BY sku
                            """)
                            .mapToMap()
                            .list());
        });
    }
}
