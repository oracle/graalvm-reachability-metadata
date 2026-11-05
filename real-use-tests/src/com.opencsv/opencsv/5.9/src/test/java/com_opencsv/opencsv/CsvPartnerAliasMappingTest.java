/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameTranslateMappingStrategy;
import com.opencsv.bean.HeaderColumnNameTranslateMappingStrategyBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's header translation strategy for importing a partner's differently named feed.
public class CsvPartnerAliasMappingTest {
    @Test
    @Timeout(120)
    public void importsPartnerColumnsIntoApplicationFields() {
        String incoming = "partner_sku,full_name,available_units\n"
                + "P-17,Mechanical keyboard,12\n"
                + "P-23,USB-C adapter,4\n";

        HeaderColumnNameTranslateMappingStrategy<PartnerProduct> mappingStrategy =
                new HeaderColumnNameTranslateMappingStrategyBuilder<PartnerProduct>().build();
        mappingStrategy.setType(PartnerProduct.class);
        Map<String, String> columnMapping = new LinkedHashMap<>();
        columnMapping.put("partner_sku", "sku");
        columnMapping.put("full_name", "name");
        columnMapping.put("available_units", "units");
        mappingStrategy.setColumnMapping(columnMapping);

        List<PartnerProduct> products = new CsvToBeanBuilder<PartnerProduct>(new StringReader(incoming))
                .withMappingStrategy(mappingStrategy)
                .build()
                .parse();

        assertEquals(2, products.size());
        assertEquals("P-17", products.get(0).getSku());
        assertEquals("Mechanical keyboard", products.get(0).getName());
        assertEquals(12, products.get(0).getUnits());
        assertEquals("P-23", products.get(1).getSku());
        assertEquals(4, products.get(1).getUnits());
    }

    public static class PartnerProduct {
        private String sku;
        private String name;
        private int units;

        public PartnerProduct() {
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public int getUnits() {
            return units;
        }
    }
}
