/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_gradle_resolver;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.util.internal.CollectionUtils;
import org.junit.jupiter.api.Test;

public class OrgGradleUtilInternalCollectionUtilsTest {
    @Test
    void collectArrayAllocatesDestinationArrayForRequestedComponentType() {
        Integer[] numbers = { 1, 2, 3 };

        Number[] collected = CollectionUtils.collectArray(numbers, Number.class, value -> value * 10L);

        assertThat(collected).containsExactly(10L, 20L, 30L);
        assertThat(collected.getClass().getComponentType()).isSameAs(Number.class);
    }
}
