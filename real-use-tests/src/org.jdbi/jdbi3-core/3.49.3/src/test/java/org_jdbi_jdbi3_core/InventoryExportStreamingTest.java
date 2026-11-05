/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's streaming result subsystem for exporting inventory rows.
public class InventoryExportStreamingTest {
    @Test
    void exportsInventoryRowsWhileConsumingTheResultStream() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:inventory_export");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE inventory_item (
                        sku VARCHAR(32) PRIMARY KEY,
                        description VARCHAR(64) NOT NULL,
                        quantity INT NOT NULL
                    )
                    """);
            handle.execute("""
                    INSERT INTO inventory_item (sku, description, quantity)
                    VALUES ('KB-01', 'Keyboard', 18),
                           ('MS-02', 'Mouse', 7),
                           ('MN-27', 'Monitor', 3)
                    """);

            String export = handle.createQuery("""
                    SELECT sku || ',' || description || ',' || quantity
                    FROM inventory_item
                    ORDER BY sku
                    """)
                    .mapTo(String.class)
                    .withStream(rows -> rows.collect(Collectors.joining("\n")));

            assertEquals("KB-01,Keyboard,18\nMN-27,Monitor,3\nMS-02,Mouse,7", export);
        });
    }
}
