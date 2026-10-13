/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

import org_springframework_modulith.named_interfaces_fixture.orders.OrderOperations;
import org_springframework_modulith.named_interfaces_fixture.orders.OrderOperations.OrderSummary;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;

public class NamedInterfacesTest {

    private static final ImportOption INCLUDE_ALL_CLASSES = location -> true;

    @Test
    void propagatesTypeBasedNamedInterfacesToRelatedApiTypes() {
        ApplicationModule module = ApplicationModules.of(NamedInterfaceModulith.class, INCLUDE_ALL_CLASSES)
                .getModuleByType(OrderOperations.class)
                .orElseThrow();
        NamedInterface namedInterface = module.getNamedInterfaces().getByName("orders").orElseThrow();

        assertThat(module.getNamedInterfaces().hasExplicitInterfaces()).isTrue();
        assertThat(namedInterface.isNamed()).isTrue();
        assertThat(namedInterface.asJavaClasses())
                .extracting(JavaClass::getName)
                .containsExactlyInAnyOrder(OrderOperations.class.getName(), OrderSummary.class.getName());
    }

    @Modulith(additionalPackages = "org_springframework_modulith.named_interfaces_fixture")
    public static class NamedInterfaceModulith {}
}
