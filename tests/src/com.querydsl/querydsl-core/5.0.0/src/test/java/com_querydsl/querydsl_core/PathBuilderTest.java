/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.ArrayPath;
import com.querydsl.core.types.dsl.PathBuilder;

import org.junit.jupiter.api.Test;

public class PathBuilderTest {

    @Test
    void getArrayCreatesTypedPropertyPathFromArrayClass() {
        PathBuilder<OrderSummary> order = new PathBuilder<OrderSummary>(OrderSummary.class, "order");

        ArrayPath<String[], String> aliases = order.getArray("aliases", String[].class);

        assertThat(aliases.getType()).isEqualTo(String[].class);
        assertThat(aliases.getElementType()).isEqualTo(String.class);
        assertThat(aliases.getMetadata().getPathType()).isEqualTo(PathType.PROPERTY);
        assertThat(aliases.getMetadata().getName()).isEqualTo("aliases");
        assertThat(aliases.getMetadata().getParent()).isSameAs(order);
        assertThat(aliases.get(0).getType()).isEqualTo(String.class);
    }

    public static final class OrderSummary {
    }
}
