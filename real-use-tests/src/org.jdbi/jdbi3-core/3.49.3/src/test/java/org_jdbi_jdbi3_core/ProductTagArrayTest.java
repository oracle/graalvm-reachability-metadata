/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Exercises Jdbi's SQL array subsystem for storing and retrieving product tags.
public class ProductTagArrayTest {
    @Test
    void storesAndReadsProductTags() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:product_tags");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE product (
                        sku VARCHAR(32) PRIMARY KEY,
                        tags VARCHAR ARRAY NOT NULL
                    )
                    """);

            handle.createUpdate("""
                    INSERT INTO product (sku, tags)
                    VALUES (:sku, :tags)
                    """)
                    .bind("sku", "KB-01")
                    .bindArray("tags", "new", "featured", "office")
                    .execute();

            String[] tags = handle.createQuery("""
                    SELECT tags
                    FROM product
                    WHERE sku = :sku
                    """)
                    .bind("sku", "KB-01")
                    .mapTo(String[].class)
                    .one();

            assertArrayEquals(new String[] {"new", "featured", "office"}, tags);
        });
    }
}
