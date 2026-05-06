/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.UnloadableClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class UnloadableClassLoaderTest {
    @Test
    void delegatesUnknownClassesToParentClassLoader() throws Exception {
        RecordingClassLoader parent = new RecordingClassLoader(UnloadableClassLoaderTest.class.getClassLoader());
        UnloadableClassLoader loader = new UnloadableClassLoader(parent);

        Class<?> loadedClass = loader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
        assertThat(parent.classNames).containsExactly(String.class.getName());
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> classNames = new ArrayList<>();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            classNames.add(name);
            return super.loadClass(name);
        }
    }
}
