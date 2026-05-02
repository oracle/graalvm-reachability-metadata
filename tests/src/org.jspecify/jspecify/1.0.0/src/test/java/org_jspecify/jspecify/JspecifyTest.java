/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jspecify.jspecify;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    void nullnessAnnotationsCanBeUsedOnArrayElementsAndVarargs() {
        ArrayBackedNullnessJournal journal = new ArrayBackedNullnessJournal(
                new @Nullable String @NonNull [] {"alpha", null});

        journal.record(new @Nullable String @NonNull [] {"beta", "gamma"});
        journal.recordVarargs("delta", null);

        assertThat(journal.snapshot()).containsExactly("alpha", null, "beta", "gamma", "delta", null);
        assertThat(journal.firstPresent(new @Nullable String @NonNull [] {null, "fallback"})).isEqualTo("fallback");
        assertThat(journal.firstPresent(new @Nullable String @NonNull [] {null, null})).isNull();
    }

    @Test
    void nullnessAnnotationsCanBeUsedOnWildcardBounds() {
        WildcardNullnessCatalog catalog = new WildcardNullnessCatalog();
        List<@Nullable String> source = new ArrayList<>();
        source.add(null);
        source.add("chosen");
        source.add("backup");
        List<CharSequence> sink = new ArrayList<>();

        catalog.copyPresentStrings(source, sink);

        assertThat(catalog.firstPresent(source)).isEqualTo("chosen");
        assertThat(sink).containsExactly("chosen", "backup");
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

    private static final class WildcardNullnessCatalog {
        private @Nullable CharSequence firstPresent(List<? extends @Nullable CharSequence> values) {
            for (@Nullable CharSequence value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private void copyPresentStrings(List<? extends @Nullable CharSequence> source,
                List<? super @NonNull String> target) {
            for (@Nullable CharSequence value : source) {
                if (value != null) {
                    target.add(value.toString());
                }
            }
        }
    }

    private static final class ArrayBackedNullnessJournal {
        private @Nullable String @NonNull [] entries;

        private ArrayBackedNullnessJournal(@Nullable String @NonNull [] initialEntries) {
            this.entries = initialEntries.clone();
        }

        private void record(@Nullable String @NonNull [] newEntries) {
            @Nullable String @NonNull [] merged = new @Nullable String @NonNull [entries.length + newEntries.length];
            System.arraycopy(entries, 0, merged, 0, entries.length);
            System.arraycopy(newEntries, 0, merged, entries.length, newEntries.length);
            entries = merged;
        }

        private void recordVarargs(@Nullable String @NonNull ... newEntries) {
            record(newEntries);
        }

        private @Nullable String firstPresent(@Nullable String @NonNull [] candidates) {
            for (@Nullable String candidate : candidates) {
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }

        private List<@Nullable String> snapshot() {
            return new ArrayList<>(Arrays.asList(entries));
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
