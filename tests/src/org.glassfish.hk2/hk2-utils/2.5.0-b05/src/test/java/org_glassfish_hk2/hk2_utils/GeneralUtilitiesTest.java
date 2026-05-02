/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.glassfish.hk2.utilities.general.GeneralUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneralUtilitiesTest {
    @Test
    public void loadsNamedClassWithProvidedClassLoader() {
        final MappingClassLoader classLoader = new MappingClassLoader();

        final Class<?> loadedClass = GeneralUtilities.loadClass(classLoader, String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(classLoader.requestedNames()).containsExactly(String.class.getName());
    }

    @Test
    public void loadsPrimitiveAndReferenceArrayClassDescriptors() {
        final MappingClassLoader classLoader = new MappingClassLoader();

        assertThat(GeneralUtilities.loadClass(classLoader, "[[I")).isEqualTo(int[][].class);
        assertThat(classLoader.requestedNames()).isEmpty();

        assertThat(GeneralUtilities.loadClass(classLoader, "[Ljava.lang.String;")).isEqualTo(String[].class);
        assertThat(classLoader.requestedNames()).containsExactly(String.class.getName());
    }

    private static final class MappingClassLoader extends ClassLoader {
        private final List<String> requestedNames = new ArrayList<>();

        private MappingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requestedNames.add(name);
            if (String.class.getName().equals(name)) {
                return String.class;
            }
            throw new ClassNotFoundException(name);
        }

        private List<String> requestedNames() {
            return Collections.unmodifiableList(requestedNames);
        }
    }
}
