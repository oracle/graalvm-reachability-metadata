/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.core.util.ClassLoaderReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class ClassLoaderReferenceTest {
    @Test
    void delegatesClassLoadingToReferencedClassLoader() throws Exception {
        ClassLoaderReference reference = new ClassLoaderReference(ClassLoaderReferenceTest.class.getClassLoader());

        Class<?> loadedClass = reference.loadClass(LoadableType.class.getName());

        assertThat(loadedClass).isEqualTo(LoadableType.class);
        assertThat(reference.getReference()).isSameAs(ClassLoaderReferenceTest.class.getClassLoader());
    }

    @Test
    void usesUpdatedReferenceForClassLoading() throws Exception {
        ClassLoader originalLoader = ClassLoaderReferenceTest.class.getClassLoader();
        RecordingClassLoader updatedLoader = new RecordingClassLoader(originalLoader);
        ClassLoaderReference reference = new ClassLoaderReference(originalLoader);

        reference.setReference(updatedLoader);
        Class<?> loadedClass = reference.loadClass(LoadableType.class.getName());

        assertThat(loadedClass).isEqualTo(LoadableType.class);
        assertThat(reference.getReference()).isSameAs(updatedLoader);
        assertThat(updatedLoader.loadedName).isEqualTo(LoadableType.class.getName());
    }

    public static final class LoadableType {
        private final String value;

        public LoadableType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String loadedName;

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedName = name;
            return super.loadClass(name);
        }
    }
}
