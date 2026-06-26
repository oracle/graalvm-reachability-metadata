/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRuntimeHintsTest {

    @Test
    void findsJacksonAnnotatedClassesInPackageAndInspectsClassMembers() {
        Set<TypeReference> annotatedTypes = AiRuntimeHints.findJsonAnnotatedClassesInPackage(AiRuntimeHintsTest.class);

        assertThat(annotatedTypes)
                .extracting(TypeReference::getName)
                .contains(JsonFieldCandidate.class.getName());
    }

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

    public static final class JsonFieldCandidate {

        @JsonProperty("value")
        public String value;

    }

    private record PlainRecordCandidate(String value) {
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
