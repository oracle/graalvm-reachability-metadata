/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jspecify.jspecify;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JspecifyTest {
    @Test
    void publicAnnotationTypesAreUsableAsJavaAnnotationTypes() {
        List<Class<? extends Annotation>> annotationTypes = List.of(
                Nullable.class,
                NonNull.class,
                NullMarked.class,
                NullUnmarked.class);

        assertThat(annotationTypes)
                .containsExactly(Nullable.class, NonNull.class, NullMarked.class, NullUnmarked.class);
    }

    @Test
    void annotationInterfacesCanBeImplementedByFrameworkStyleProxies() {
        Nullable nullable = new Nullable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
        NonNull nonNull = new NonNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NonNull.class;
            }
        };
        NullMarked nullMarked = new NullMarked() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NullMarked.class;
            }
        };
        NullUnmarked nullUnmarked = new NullUnmarked() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NullUnmarked.class;
            }
        };

        assertThat(nullable.annotationType()).isEqualTo(Nullable.class);
        assertThat(nonNull.annotationType()).isEqualTo(NonNull.class);
        assertThat(nullMarked.annotationType()).isEqualTo(NullMarked.class);
        assertThat(nullUnmarked.annotationType()).isEqualTo(NullUnmarked.class);
    }

    @Test
    void nullnessAnnotationsCanBeUsedOnFieldsMethodsParametersAndGenericTypeUses() {
        NullMarkedDirectory directory = new NullMarkedDirectory("primary");

        directory.addLabel("numbers", "one");
        directory.addLabel("numbers", null);
        directory.addLabel("letters", "a");
        directory.setLegacyAlias(null);

        assertThat(directory.labelsFor("numbers")).containsExactly("one", null);
        assertThat(directory.labelsFor("letters")).containsExactly("a");
        assertThat(directory.findFirstLabel("missing")).isNull();
        assertThat(directory.aliases()).containsExactly("primary", null);
        assertThat(directory.copyNullableValues(List.of("left", "right"))).containsExactly("left", "right");
    }

    @Test
    void nullUnmarkedBoundariesCanBeNestedInsideNullMarkedCode() {
        LegacyBoundary boundary = new LegacyBoundary(null);

        assertThat(boundary.normalize(null)).isEqualTo("fallback");
        assertThat(boundary.normalize("custom")).isEqualTo("custom");
        assertThat(boundary.value()).isNull();
    }

    @NullMarked
    private static final class NullMarkedDirectory {
        private final Map<@NonNull String, List<@Nullable String>> labelsByKey = new LinkedHashMap<>();
        private final String primaryAlias;
        private @Nullable String legacyAlias;

        @NullMarked
        private NullMarkedDirectory(String primaryAlias) {
            this.primaryAlias = primaryAlias;
        }

        private void addLabel(String key, @Nullable String label) {
            labelsByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(label);
        }

        private @Nullable String findFirstLabel(String key) {
            List<@Nullable String> labels = labelsByKey.get(key);
            if (labels == null || labels.isEmpty()) {
                return null;
            }
            return labels.get(0);
        }

        private List<@Nullable String> labelsFor(String key) {
            return labelsByKey.getOrDefault(key, List.of());
        }

        private List<@Nullable CharSequence> aliases() {
            List<@Nullable CharSequence> aliases = new ArrayList<>();
            aliases.add(primaryAlias);
            aliases.add(legacyAlias);
            return aliases;
        }

        private <T extends @Nullable CharSequence> List<T> copyNullableValues(List<T> values) {
            return new ArrayList<>(values);
        }

        @NullUnmarked
        private void setLegacyAlias(@Nullable String legacyAlias) {
            this.legacyAlias = legacyAlias;
        }
    }

    @NullUnmarked
    private static final class LegacyBoundary {
        private final String value;

        @NullUnmarked
        private LegacyBoundary(String value) {
            this.value = value;
        }

        @NullMarked
        private String normalize(@Nullable String input) {
            return input == null ? "fallback" : input;
        }

        private @Nullable String value() {
            return value;
        }
    }
}
