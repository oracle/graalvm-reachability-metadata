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
    @Test
    void openMocksReportsProxyMockMakerCannotSpyConcreteClass() {
        NoArgSpyFixture fixture = new NoArgSpyFixture();

        assertThatThrownBy(() -> MockitoAnnotations.openMocks(fixture))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("Unable to initialize @Spy annotated field 'collaborator'")
                .hasMessageContaining("Cannot mock/spy class")
                .hasMessageContaining("non-interface");
    }

    @Test
    void openMocksReportsUninitializedSpyWithoutNoArgConstructor() {
        ConstructorOnlySpyFixture fixture = new ConstructorOnlySpyFixture();

        assertThatThrownBy(() -> MockitoAnnotations.openMocks(fixture))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("Unable to initialize @Spy annotated field 'collaborator'")
                .hasMessageContaining(
                        "Please ensure that the type 'ConstructorOnlyCollaborator'"
                                + " has a no-arg constructor.");
    }

    static final class NoArgSpyFixture {
        @Spy
        private NoArgCollaborator collaborator;
    }

    static final class ConstructorOnlySpyFixture {
        @Spy
        private ConstructorOnlyCollaborator collaborator;
    }

    static final class NoArgCollaborator {
        NoArgCollaborator() { }
    }

    static final class ConstructorOnlyCollaborator {
        private final String name;

        ConstructorOnlyCollaborator(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }
}
