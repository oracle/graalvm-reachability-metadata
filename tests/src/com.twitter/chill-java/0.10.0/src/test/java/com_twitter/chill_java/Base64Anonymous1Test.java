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
        assertThat(loader.requestedClassNames()).contains(ArrayList.class.getName());
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedClassNames = new ArrayList<>();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedClassNames.add(name);
            return super.loadClass(name);
        }

        private List<String> requestedClassNames() {
            return requestedClassNames;
        }
    }
}
