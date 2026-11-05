/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.BeanVerifier;
import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.CsvToBeanFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises OpenCSV's filtered bean ingestion and split-field conversion for an active product catalog.
public class CsvCatalogFilterTest {
    @Test
    @Timeout(120)
    public void importsActiveProductsWithNormalizedTags() {
        String incoming = "sku,name,tags,quantity,status\n"
                + "P-17,Mechanical keyboard,office|mechanical,12,ACTIVE\n"
                + "P-23,USB-C adapter,travel|usb-c,0,ACTIVE\n"
                + "P-31,Wireless mouse,office|wireless,4,DISCONTINUED\n";

        List<ProductRow> products = new CsvToBeanBuilder<ProductRow>(new StringReader(incoming))
                .withType(ProductRow.class)
                .withFilter(new ActiveProductFilter())
                .withVerifier(new InStockVerifier())
                .build()
                .parse();

        assertEquals(1, products.size());
        ProductRow product = products.get(0);
        assertEquals("P-17", product.getSku());
        assertEquals("Mechanical keyboard", product.getName());
        assertIterableEquals(List.of("office", "mechanical"), product.getTags());
        assertEquals(12, product.getQuantity());
        assertEquals("ACTIVE", product.getStatus());
    }

    public static class ProductRow {
        @CsvBindByName(column = "sku", required = true)
        private String sku;

        @CsvBindByName(column = "name", required = true)
        private String name;

        @CsvBindAndSplitByName(column = "tags", elementType = String.class, splitOn = "\\|", required = true)
        private List<String> tags;

        @CsvBindByName(column = "quantity", required = true)
        private int quantity;

        @CsvBindByName(column = "status", required = true)
        private String status;

        public ProductRow() {
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getStatus() {
            return status;
        }
    }

    private static class ActiveProductFilter implements CsvToBeanFilter {
        @Override
        public boolean allowLine(String[] line) {
            return "ACTIVE".equals(line[4]);
        }
    }

    private static class InStockVerifier implements BeanVerifier<ProductRow> {
        @Override
        public boolean verifyBean(ProductRow product) {
            return product.getQuantity() > 0;
        }
    }
}
