/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's bean-mapping subsystem for loading catalog items into an application model.
public class CatalogBeanMappingTest {
    @Test
    void loadsCatalogItemsIntoApplicationBeans() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:catalog_beans");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE catalog_item (
                        sku VARCHAR(32) PRIMARY KEY,
                        display_name VARCHAR(64) NOT NULL,
                        price_cents INT NOT NULL
                    )
                    """);
            handle.execute("""
                    INSERT INTO catalog_item (sku, display_name, price_cents)
                    VALUES ('KB-01', 'Keyboard', 4900), ('MS-02', 'Mouse', 1900)
                    """);

            List<CatalogItem> catalog = handle.createQuery("""
                    SELECT sku, display_name, price_cents
                    FROM catalog_item
                    ORDER BY sku
                    """)
                    .map(BeanMapper.of(CatalogItem.class))
                    .list();

            assertEquals(List.of(
                    new CatalogItem("KB-01", "Keyboard", 4900),
                    new CatalogItem("MS-02", "Mouse", 1900)), catalog);
        });
    }

    public static final class CatalogItem {
        private String sku;
        private String displayName;
        private int priceCents;

        public CatalogItem() {
        }

        public CatalogItem(String sku, String displayName, int priceCents) {
            this.sku = sku;
            this.displayName = displayName;
            this.priceCents = priceCents;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getPriceCents() {
            return priceCents;
        }

        public void setPriceCents(int priceCents) {
            this.priceCents = priceCents;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CatalogItem item)) {
                return false;
            }
            return priceCents == item.priceCents
                    && sku.equals(item.sku)
                    && displayName.equals(item.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku, displayName, priceCents);
        }
    }
}
