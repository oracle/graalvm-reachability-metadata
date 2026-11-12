/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.reactive.loader.ast.internal.ReactiveLoaderHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveLoaderHelperTest {
    @Test
    void createsTypedIdentifierArraysForCollectionConversion() {
        Long[] identifiers = List.of(11L, 22L).toArray(ReactiveLoaderHelper.createTypedArray(Long.class, 0));
        assertEquals(Long[].class, identifiers.getClass());
        assertArrayEquals(new Long[]{11L, 22L}, identifiers);

        String[] labels = ReactiveLoaderHelper.createTypedArray(String.class, 2);
        labels[0] = "first";
        labels[1] = "second";
        assertArrayEquals(new String[]{"first", "second"}, labels);
    }
}
