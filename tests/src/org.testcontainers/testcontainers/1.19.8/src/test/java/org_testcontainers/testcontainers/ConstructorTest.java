/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.LoaderOptions;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.constructor.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorTest {
    @Test
    void resolvesRootTypesNamedInConstructorConfiguration() throws Exception {
        String rootType = Bean.class.getName();
        Bean first = new Yaml(new Constructor(rootType)).load("value: first");
        Bean second = new Yaml(new Constructor(rootType, new LoaderOptions())).load("value: second");
        Thread thread = Thread.currentThread();
        ClassLoader contextLoader = thread.getContextClassLoader();
        Constructor fallbackConstructor;
        try {
            thread.setContextClassLoader(new DenyingClassLoader());
            fallbackConstructor = new Constructor(rootType);
        } finally {
            thread.setContextClassLoader(contextLoader);
        }
        Bean fallback = new Yaml(fallbackConstructor).load("value: fallback");

        assertThat(first.value).isEqualTo("first");
        assertThat(second.value).isEqualTo("second");
        assertThat(fallback.value).isEqualTo("fallback");
    }

    public static class Bean {
        public String value;
    }

    public static class DenyingClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
