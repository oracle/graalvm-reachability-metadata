/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.Base64;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Base64Anonymous1Test {
    @Test
    void decodesSerializedObjectWithProvidedClassLoader() throws Exception {
        Payload original = new Payload("chill-base64-custom-loader", 17);
        RecordingClassLoader loader = new RecordingClassLoader(Base64Anonymous1Test.class.getClassLoader());

        String encoded = Base64.encodeObject(original);
        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, loader);

        assertThat(decoded).isInstanceOf(Payload.class);
        Payload payload = (Payload) decoded;
        assertThat(payload.name).isEqualTo(original.name);
        assertThat(payload.count).isEqualTo(original.count);
        assertThat(loader.loadedClassNames()).contains(Payload.class.getName());
    }

    private static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        private Payload(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name, resolve);
        }

        private List<String> loadedClassNames() {
            return loadedClassNames;
        }
    }

}
