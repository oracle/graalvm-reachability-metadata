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

public class FieldInitializerInnerNoArgConstructorInstantiatorTest {
    @Mock
    private Collaborator collaborator;

    @InjectMocks
    private NoArgService service;

    @Test
    void injectMocksInstantiatesFieldWithNoArgConstructorBeforeInjection() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            assertThat(service).isNotNull();
            assertThat(service.collaborator()).isSameAs(collaborator);
        }
    }

    interface Collaborator {}

    public static class NoArgService {
        private Collaborator collaborator;

        public NoArgService() {}

        public Collaborator collaborator() {
            return collaborator;
        }
    }
}
