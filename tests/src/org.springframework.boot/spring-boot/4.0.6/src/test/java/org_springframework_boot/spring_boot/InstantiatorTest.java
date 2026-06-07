/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;

import org.springframework.boot.util.Instantiator;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiatorTest {

    @Test
    void instantiateTypeUsesMatchingConstructorWithAvailableParameters() {
        Instantiator<Service> instantiator = new Instantiator<>(Service.class, (parameters) -> {
            parameters.add(String.class, "alpha");
            parameters.add(Integer.class, (factoryType) -> factoryType.getName().length());
        });

        Service service = instantiator.instantiateType(ConstructorInjectedService.class);

        assertThat(service).isInstanceOf(ConstructorInjectedService.class);
        ConstructorInjectedService injectedService = (ConstructorInjectedService) service;
        assertThat(injectedService.name).isEqualTo("alpha");
        assertThat(injectedService.factoryValue).isEqualTo(Service.class.getName().length());
    }

    public interface Service {

    }

    public static final class ConstructorInjectedService implements Service {

        private final String name;

        private final int factoryValue;

        public ConstructorInjectedService(String name, Integer factoryValue) {
            this.name = name;
            this.factoryValue = factoryValue;
        }

    }

}
