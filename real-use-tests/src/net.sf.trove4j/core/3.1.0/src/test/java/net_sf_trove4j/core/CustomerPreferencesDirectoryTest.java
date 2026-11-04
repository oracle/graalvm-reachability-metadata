/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TIntObjectHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's primitive-object map subsystem to maintain customer preferences.
public class CustomerPreferencesDirectoryTest {
    @Test
    @Timeout(60)
    public void updatesPreferencesWhileKeepingCustomerDirectoryConsistent() {
        TIntObjectHashMap<CustomerPreferences> preferencesByCustomer =
                new TIntObjectHashMap<>();
        CustomerPreferences initialPreferences = new CustomerPreferences("dark", "weekly");
        CustomerPreferences updatedPreferences = new CustomerPreferences("light", "daily");
        preferencesByCustomer.put(17, initialPreferences);
        preferencesByCustomer.put(23, new CustomerPreferences("dark", "never"));

        assertTrue(preferencesByCustomer.containsKey(17));
        assertEquals(initialPreferences, preferencesByCustomer.get(17));

        preferencesByCustomer.put(17, updatedPreferences);
        preferencesByCustomer.remove(23);

        assertEquals(1, preferencesByCustomer.size());
        assertEquals(updatedPreferences, preferencesByCustomer.get(17));
        assertFalse(preferencesByCustomer.containsKey(23));
        assertEquals("daily", preferencesByCustomer.get(17).digestFrequency());
    }

    private record CustomerPreferences(String theme, String digestFrequency) {
    }
}
