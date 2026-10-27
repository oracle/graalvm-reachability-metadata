/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

/** Verifies collection-to-array conversion with element conversion. */
public class CollectionToArrayConverterTest {
    @Test
    void convertsCollectionElementsIntoTypedArray() {
        DefaultConversionService service = new DefaultConversionService();

        Integer[] converted = service.convert(Arrays.asList("1", "2", "3"), Integer[].class);

        assertThat(converted).containsExactly(1, 2, 3);
    }
}
