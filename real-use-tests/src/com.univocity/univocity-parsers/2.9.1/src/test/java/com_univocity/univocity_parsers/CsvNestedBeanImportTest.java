/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Nested;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the nested bean-mapping subsystem to import customer profiles with contact details.
 */
public class CsvNestedBeanImportTest {
    @Test
    @Timeout(60)
    public void importsCustomerProfilesIntoNestedContactDetails() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        CsvRoutines routines = new CsvRoutines(settings);
        String profileFeed = "customer_id,customer_name,email,phone\n"
                + "C-1042,Ada Lovelace,ada@example.test,+1-555-0104\n"
                + "C-1043,Grace Hopper,grace@example.test,+1-555-0105\n";

        List<CustomerProfile> profiles = routines.parseAll(
                CustomerProfile.class, new StringReader(profileFeed));

        assertEquals(2, profiles.size());
        CustomerProfile ada = profiles.get(0);
        assertEquals("C-1042", ada.getCustomerId());
        assertEquals("Ada Lovelace", ada.getCustomerName());
        assertNotNull(ada.getContact());
        assertEquals("ada@example.test", ada.getContact().getEmail());
        assertEquals("+1-555-0104", ada.getContact().getPhone());
        assertEquals("Grace Hopper", profiles.get(1).getCustomerName());
        assertEquals("grace@example.test", profiles.get(1).getContact().getEmail());
    }

    public static class CustomerProfile {
        @Parsed(field = "customer_id")
        private String customerId;

        @Parsed(field = "customer_name")
        private String customerName;

        @Nested
        private ContactDetails contact = new ContactDetails();

        public String getCustomerId() {
            return customerId;
        }

        public String getCustomerName() {
            return customerName;
        }

        public ContactDetails getContact() {
            return contact;
        }
    }

    public static class ContactDetails {
        @Parsed(field = "email")
        private String email;

        @Parsed(field = "phone")
        private String phone;

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }
    }
}
