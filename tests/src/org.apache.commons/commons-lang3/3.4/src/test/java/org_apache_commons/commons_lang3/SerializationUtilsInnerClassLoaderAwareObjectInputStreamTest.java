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

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsInnerClassLoaderAwareObjectInputStreamTest {

    @Test
    public void cloneFallsBackToThreadContextClassLoaderForNestedPayloads() {
        final ArrayList<Serializable> original = new ArrayList<>();
        original.add(new ContextClassLoaderPayload("context-loaded-payload"));

        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ContextClassLoaderPayload.class.getClassLoader());
        try {
            final ArrayList<Serializable> clone = SerializationUtils.clone(original);

            assertThat(clone).isNotSameAs(original);
            assertThat(clone).hasSize(1);
            assertThat(clone.get(0)).isInstanceOf(ContextClassLoaderPayload.class);
            assertThat(clone.get(0)).isNotSameAs(original.get(0));
            assertThat(((ContextClassLoaderPayload) clone.get(0)).getValue()).isEqualTo("context-loaded-payload");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class ContextClassLoaderPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public ContextClassLoaderPayload(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
