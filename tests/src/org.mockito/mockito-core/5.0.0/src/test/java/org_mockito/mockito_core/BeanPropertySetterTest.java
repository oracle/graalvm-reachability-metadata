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

public class BeanPropertySetterTest {
    @Mock
    private Collaborator collaborator;

    @InjectMocks
    private SetterInjectedService service;

    @Test
    void injectMocksUsesBeanSetterForMatchingCollaborator() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            assertThat(service.collaborator()).isSameAs(collaborator);
        }
    }

    interface Collaborator {}

    public static class SetterInjectedService {
        private Collaborator collaborator;

        public void setCollaborator(Collaborator collaborator) {
            this.collaborator = collaborator;
        }

        public Collaborator collaborator() {
            return collaborator;
        }
    }
}
