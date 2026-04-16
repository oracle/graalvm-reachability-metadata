/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsClassLoaderAwareObjectInputStreamTest {

    @Test
    void cloneUsesTheOriginalObjectClassLoaderToResolveApplicationTypes() {
        final LoaderAwareEnvelope original = new LoaderAwareEnvelope(
                "commons-lang",
                new ArrayList<>(List.of("clone"))
        );

        final LoaderAwareEnvelope cloned = SerializationUtils.clone(original);
        original.getTags().add("mutated-after-clone");

        assertThat(cloned).isNotNull();
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getName()).isEqualTo("commons-lang");
        assertThat(cloned.getTags()).containsExactly("clone");
        assertThat(cloned.getTags()).isNotSameAs(original.getTags());
    }

    @Test
    void cloneFallsBackToTheThreadContextClassLoaderForNestedApplicationTypes() {
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            final ArrayList<Serializable> original = new ArrayList<>();
            original.add(new LoaderAwareEnvelope(
                    "commons-lang",
                    new ArrayList<>(List.of("context-class-loader"))
            ));

            final ArrayList<Serializable> cloned = SerializationUtils.clone(original);
            final LoaderAwareEnvelope clonedEnvelope = (LoaderAwareEnvelope) cloned.get(0);
            ((LoaderAwareEnvelope) original.get(0)).getTags().add("mutated-after-clone");

            assertThat(cloned).isNotNull();
            assertThat(cloned).isNotSameAs(original);
            assertThat(clonedEnvelope.getName()).isEqualTo("commons-lang");
            assertThat(clonedEnvelope.getTags()).containsExactly("context-class-loader");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class LoaderAwareEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> tags;

        private LoaderAwareEnvelope(final String name, final List<String> tags) {
            this.name = name;
            this.tags = tags;
        }

        private String getName() {
            return name;
        }

        private List<String> getTags() {
            return tags;
        }
    }
}
