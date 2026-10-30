/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.SpringFactoriesLoader;

/** Verifies default constructor selection when a factory has several constructors. */
public class SpringFactoriesLoaderInnerFactoryInstantiatorTest {
    @Test
    void selectsDefaultConstructorForOverloadedFactory() {
        SpringFactoriesLoader loader = new ConfiguredLoader();

        assertThat(loader.load(Service.class)).singleElement().extracting(Service::name).isEqualTo("default");
    }

    public interface Service {
        String name();
    }

    public static class OverloadedService implements Service {
        private final String name;

        public OverloadedService() {
            this.name = "default";
        }

        public OverloadedService(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static class ConfiguredLoader extends SpringFactoriesLoader {
        ConfiguredLoader() {
            super(SpringFactoriesLoaderInnerFactoryInstantiatorTest.class.getClassLoader(),
                    Map.of(Service.class.getName(), List.of(OverloadedService.class.getName())));
        }
    }
}
