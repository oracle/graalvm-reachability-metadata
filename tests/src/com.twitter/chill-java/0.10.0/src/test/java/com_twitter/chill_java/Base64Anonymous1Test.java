/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.Base64;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Base64Anonymous1Test {
    @Test
    void decodesObjectUsingProvidedClassLoader() throws Exception {
        ArrayList<String> original = new ArrayList<>();
        original.add("first");
        original.add("second");
        RecordingClassLoader loader = new RecordingClassLoader(getClass().getClassLoader());

        String encoded = Base64.encodeObject(original);
        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, loader);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void failsBeforeSuperclassFallbackWhenProvidedClassLoaderCannotResolveSerializedClass() throws Exception {
        // `Base64$1.resolveClass` can only call its superclass fallback if `Class.forName` returns null.
        // The Java API returns a class or throws instead, so this public path documents the failure branch.
        ArrayList<String> original = new ArrayList<>();
        original.add("blocked");
        String encoded = Base64.encodeObject(original);
        BlockingClassLoader loader = new BlockingClassLoader(getClass().getClassLoader(), ArrayList.class.getName());

        try {
            Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, loader);
            assertThat(decoded).isEqualTo(original);
        } catch (ClassNotFoundException expected) {
            assertThat(expected).hasMessageContaining(ArrayList.class.getName());
            assertThat(loader.requestedClassNames()).contains(ArrayList.class.getName());
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static final class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;
        private final List<String> requestedClassNames = new ArrayList<>();

        private BlockingClassLoader(ClassLoader parent, String blockedClassName) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedClassNames.add(name);
            if (blockedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private List<String> requestedClassNames() {
            return requestedClassNames;
        }
    }
}
