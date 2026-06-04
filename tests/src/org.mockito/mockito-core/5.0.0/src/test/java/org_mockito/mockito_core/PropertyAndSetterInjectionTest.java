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
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock private SetterCollaborator setterCollaborator;
    @Mock private FieldCollaborator fieldCollaborator;
    @InjectMocks private InjectionTarget injectionTarget;

    @Test
    void openMocksInjectsMockCandidatesIntoPropertiesAndFields() throws Exception {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
            assertThat(injectionTarget.hasSetterCollaborator(setterCollaborator)).isTrue();
            assertThat(injectionTarget.hasFieldCollaborator(fieldCollaborator)).isTrue();
        }
    }

    public interface SetterCollaborator {
    }

    public interface FieldCollaborator {
    }

    public static class InjectionTarget {
        private SetterCollaborator setterCollaborator;
        private FieldCollaborator fieldCollaborator;

        public void setSetterCollaborator(SetterCollaborator setterCollaborator) {
            this.setterCollaborator = setterCollaborator;
        }

        boolean hasSetterCollaborator(SetterCollaborator expected) {
            return setterCollaborator == expected;
        }

        boolean hasFieldCollaborator(FieldCollaborator expected) {
            return fieldCollaborator == expected;
        }
    }
}
