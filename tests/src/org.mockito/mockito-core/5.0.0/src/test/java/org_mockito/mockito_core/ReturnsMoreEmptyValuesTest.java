/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class ReturnsMoreEmptyValuesTest {
    interface ArrayReturningService {
        String[] names();
    }

    @Test
    void smartNullsReturnEmptyArrayForUnstubbedArrayMethod() {
        ArrayReturningService service =
                Mockito.mock(ArrayReturningService.class, Mockito.RETURNS_SMART_NULLS);

        assertThat(service.names()).isEmpty();
    }
}
