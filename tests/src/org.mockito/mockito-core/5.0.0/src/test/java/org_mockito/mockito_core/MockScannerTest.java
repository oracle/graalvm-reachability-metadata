/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class MockScannerTest {
    @Mock
    private Collaborator collaborator;

    @Test
    void annotationInitializationScansMockFields() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            assertThat(collaborator).isNotNull();
        }
    }

    interface Collaborator {}
}
