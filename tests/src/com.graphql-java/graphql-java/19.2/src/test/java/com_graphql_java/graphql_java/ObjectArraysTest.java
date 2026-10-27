/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

public class ObjectArraysTest {
    @Test
    void createsArrayWithRequestedRuntimeComponentType() {
        String[] fields = ObjectArrays.newArray(String.class, 3);

        assertThat(fields).containsExactly(null, null, null);
        assertThat(fields.getClass().getComponentType()).isSameAs(String.class);
    }
}
