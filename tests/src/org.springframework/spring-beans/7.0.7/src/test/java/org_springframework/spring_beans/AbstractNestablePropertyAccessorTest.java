/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractNestablePropertyAccessorTest {
    @Test
    void growsNestedObjectsAndArraysWhenBindingIndexedPaths() {
        Form form = new Form();
        DirectFieldAccessor accessor = new DirectFieldAccessor(form);
        accessor.setAutoGrowNestedPaths(true);
        accessor.setPropertyValue("address.city", "Paris");
        accessor.setPropertyValue("addresses[2].city", "Oslo");
        accessor.setPropertyValue("numbers[3]", "17");
        accessor.setPropertyValue("matrix[0][1]", "23");
        assertEquals("Paris", form.address.city);
        assertEquals(3, form.addresses.length);
        assertNotNull(form.addresses[0]);
        assertEquals("Oslo", form.addresses[2].city);
        assertEquals(17, form.numbers[3]);
        assertEquals(23, form.matrix[0][1]);
    }

    public static class Form {
        private Address address;
        private Address[] addresses;
        private int[] numbers;
        private int[][] matrix;
    }

    public static class Address {
        private String city;
    }
}
