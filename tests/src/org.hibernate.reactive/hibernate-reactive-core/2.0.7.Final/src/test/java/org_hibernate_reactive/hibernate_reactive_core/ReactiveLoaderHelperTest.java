/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.reactive.loader.ast.internal.ReactiveLoaderHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveLoaderHelperTest {

    @Test
    void createsTypedReferenceArray() {
        String[] values = ReactiveLoaderHelper.createTypedArray(String.class, 3);

        assertThat(values)
                .hasSize(3)
                .containsOnlyNulls();
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
    }
}
