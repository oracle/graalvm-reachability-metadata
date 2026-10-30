/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.SpringFactoriesLoader;

/** Verifies classpath factory discovery and reflective construction. */
public class SpringFactoriesLoaderTest {
    @Test
    void loadsAndInstantiatesConfiguredFactory() {
        List<TestFactory> factories =
                SpringFactoriesLoader.loadFactories(TestFactory.class, getClass().getClassLoader());

        assertThat(factories).singleElement().extracting(TestFactory::name).isEqualTo("spring");
    }

    public interface TestFactory {
        String name();
    }

    public static final class FactoryImplementation implements TestFactory {
        public FactoryImplementation() { }

        @Override
        public String name() {
            return "spring";
        }
    }
}
