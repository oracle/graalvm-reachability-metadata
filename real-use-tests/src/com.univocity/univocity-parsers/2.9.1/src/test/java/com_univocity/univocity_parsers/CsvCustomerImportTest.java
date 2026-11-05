/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Trim;
import com.univocity.parsers.conversions.IntegerConversion;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the CSV bean-mapping subsystem to import a customer loyalty feed.
 */
public class CsvCustomerImportTest {
    @Test
    @Timeout(60)
    public void importsQuotedCustomerNamesAndConvertsLoyaltyPoints() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        CsvRoutines routines = new CsvRoutines(settings);
        String customerFeed = """
                customer_id,full_name,points,notes
                C-1042,"Ada Lovelace, VIP", 120,early adopter
                C-1043,"Grace Hopper", 85,compiler workshop
                """;

        List<Customer> customers = routines.parseAll(Customer.class, new StringReader(customerFeed));

        assertEquals(2, customers.size());
        assertEquals("C-1042", customers.get(0).getCustomerId());
        assertEquals("Ada Lovelace, VIP", customers.get(0).getFullName());
        assertEquals(120, customers.get(0).getPoints());
        assertEquals("early adopter", customers.get(0).getNotes());
        assertEquals("C-1043", customers.get(1).getCustomerId());
        assertEquals("Grace Hopper", customers.get(1).getFullName());
        assertEquals(85, customers.get(1).getPoints());
    }

    public static class Customer {
        @Parsed(field = "customer_id")
        private String customerId;

        @Parsed(field = "full_name")
        private String fullName;

        @Parsed(field = "points")
        @Convert(conversionClass = IntegerConversion.class)
        private Integer points;

        @Parsed(field = "notes")
        @Trim
        private String notes;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public Integer getPoints() {
            return points;
        }

        public void setPoints(Integer points) {
            this.points = points;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
