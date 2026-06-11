/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyAndSetterInjectionTest {
    @Mock
    private PrimaryDependency primaryDependency;

    @Mock
    private SecondaryDependency secondaryDependency;

    @InjectMocks
    private Service service;

    @Test
    void injectMocksOrdersFieldsAndInjectsMatchingMocks() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            assertThat(service.primaryDependency).isSameAs(primaryDependency);
            assertThat(service.secondaryDependency).isSameAs(secondaryDependency);
        }
    }

    interface PrimaryDependency {}

    interface SecondaryDependency {}

    public static class Service {
        private PrimaryDependency primaryDependency;
        private SecondaryDependency secondaryDependency;
    }
}
