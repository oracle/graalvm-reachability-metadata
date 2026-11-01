/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.SimpleTypeConverter;

import static org.junit.jupiter.api.Assertions.*;

public class TypeConverterDelegateTest {
    public enum Priority { LOW, HIGH }

    @Test
    void convertsEnumsCollectionsArraysAndScalarValues() {
        SimpleTypeConverter converter = new SimpleTypeConverter();
        assertSame(Priority.HIGH, converter.convertIfNecessary("HIGH", Priority.class));
        assertArrayEquals(new Integer[]{4, 9},
                (Integer[]) converter.convertIfNecessary(List.of("4", "9"), Integer[].class));
        assertArrayEquals(new Integer[]{3, 8},
                (Integer[]) converter.convertIfNecessary(new String[]{"3", "8"}, Integer[].class));
        assertArrayEquals(new Integer[]{7},
                (Integer[]) converter.convertIfNecessary("7", Integer[].class));
    }
}
