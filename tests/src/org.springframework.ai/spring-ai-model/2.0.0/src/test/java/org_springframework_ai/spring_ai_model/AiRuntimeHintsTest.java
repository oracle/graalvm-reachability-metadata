/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRuntimeHintsTest {

    @Test
    void findsNestedClassesRecursively() {
        Set<TypeReference> nestedTypes = AiRuntimeHints.findInnerClassesFor(NestedRoot.class);

        assertThat(nestedTypes)
                .extracting(TypeReference::getName)
                .contains(
                        NestedRoot.PrivateNested.class.getName(),
                        NestedRoot.PrivateNested.DeepNested.class.getName(),
                        ParentWithPublicNested.PublicInheritedNested.class.getName());
    }

    private static class ParentWithPublicNested {

        public static class PublicInheritedNested {
        }

    }

    private static final class NestedRoot extends ParentWithPublicNested {

        private static final class PrivateNested {

            private static final class DeepNested {
            }

        }

    }

}
