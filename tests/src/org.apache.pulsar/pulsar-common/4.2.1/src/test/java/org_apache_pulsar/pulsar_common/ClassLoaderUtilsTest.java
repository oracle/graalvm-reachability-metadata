/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.pulsar.common.compression.CompressionCodec;
import org.apache.pulsar.common.compression.CompressionCodecNone;
import org.apache.pulsar.common.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilsTest {
    private static final String PLUGIN_CLASS_NAME = "org.apache.pulsar.common.util.CustomCompressionCodec";

    @Test
    void usesProvidedClassLoaderWhenDefaultLookupDoesNotFindClass() throws Exception {
        final AliasClassLoader classLoader = new AliasClassLoader(
                ClassLoaderUtils.class.getClassLoader(), PLUGIN_CLASS_NAME, CompressionCodecNone.class);

        final Class<?> loadedClass = ClassLoaderUtils.loadClass(PLUGIN_CLASS_NAME, classLoader);

        assertThat(loadedClass).isEqualTo(CompressionCodecNone.class);
        assertThat(classLoader.requestedNames()).containsExactly(PLUGIN_CLASS_NAME);
    }

    @Test
    void validatesAssignableClassResolvedByProvidedClassLoader() {
        final AliasClassLoader classLoader = new AliasClassLoader(
                ClassLoaderUtils.class.getClassLoader(), PLUGIN_CLASS_NAME, CompressionCodecNone.class);

        ClassLoaderUtils.implementsClass(PLUGIN_CLASS_NAME, CompressionCodec.class, classLoader);

        assertThat(classLoader.requestedNames()).containsExactly(PLUGIN_CLASS_NAME);
    }

    private static final class AliasClassLoader extends ClassLoader {
        private final String aliasClassName;
        private final Class<?> resolvedClass;
        private final List<String> requestedNames = new ArrayList<>();

        private AliasClassLoader(ClassLoader parent, String aliasClassName, Class<?> resolvedClass) {
            super(parent);
            this.aliasClassName = aliasClassName;
            this.resolvedClass = resolvedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (aliasClassName.equals(name)) {
                requestedNames.add(name);
                return resolvedClass;
            }
            return super.loadClass(name);
        }

        private List<String> requestedNames() {
            return requestedNames;
        }
    }
}
