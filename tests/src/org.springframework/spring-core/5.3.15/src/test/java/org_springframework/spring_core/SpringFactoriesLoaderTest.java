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

/** Verifies classpath discovery and reflective construction of Spring factories. */
public class SpringFactoriesLoaderTest {
    @Test
    void loadsConfiguredFactory() {
        List<Factory> factories =
                SpringFactoriesLoader.loadFactories(Factory.class, getClass().getClassLoader());

        assertThat(factories).singleElement().extracting(Factory::name).isEqualTo("spring-core");
    }

    public interface Factory {
        String name();
    }

    public static final class FactoryImplementation implements Factory {
        public FactoryImplementation() {}

        @Override
        public String name() {
            return "spring-core";
        }
    }
}
