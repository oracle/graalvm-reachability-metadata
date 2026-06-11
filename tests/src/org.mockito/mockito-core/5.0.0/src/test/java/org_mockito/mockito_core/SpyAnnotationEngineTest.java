/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.exceptions.base.MockitoException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SpyAnnotationEngineTest {
    @Spy
    private SpyTarget target;

    @Test
    void openMocksReportsThatProxyMockMakerCannotSpyConcreteClass() {
        assertThatThrownBy(() -> MockitoAnnotations.openMocks(this))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("Unable to initialize @Spy annotated field 'target'");
    }

    public static class SpyTarget {
        public String message() {
            return "real";
        }
    }
}
