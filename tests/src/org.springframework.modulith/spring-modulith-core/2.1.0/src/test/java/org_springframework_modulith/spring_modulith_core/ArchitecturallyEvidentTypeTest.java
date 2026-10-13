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
import org.springframework.modulith.core.ArchitecturallyEvidentType;
import org.springframework.modulith.core.ArchitecturallyEvidentType.ReferenceMethod;
import org.springframework.modulith.core.SpringBean;

import org_springframework_modulith.architecture_fixture.orders.OrderEventListener;
import org_springframework_modulith.architecture_fixture.orders.OrderEventListener.OrderCompleted;

import com.tngtech.archunit.core.importer.ImportOption;

public class ArchitecturallyEvidentTypeTest {

    private static final ImportOption INCLUDE_ALL_CLASSES = location -> true;

    @Test
    void identifiesSpringBeanRolesAndEventTypes() {
        ApplicationModule module = ApplicationModules.of(ArchitectureModulith.class, INCLUDE_ALL_CLASSES)
                .getModuleByType(OrderEventListener.class)
                .orElseThrow();
        SpringBean bean = module.getSpringBeans(OrderEventListener.class).stream().findFirst().orElseThrow();
        ArchitecturallyEvidentType type = bean.toArchitecturallyEvidentType();
        ReferenceMethod listener = type.getReferenceMethods().findFirst().orElseThrow();

        assertThat(bean.getFullyQualifiedTypeName()).isEqualTo(OrderEventListener.class.getName());
        assertThat(bean.isAssignableTo(OrderEventListener.class)).isTrue();
        assertThat(type.isService()).isTrue();
        assertThat(type.isEventListener()).isTrue();
        assertThat(type.isInjectable()).isTrue();
        assertThat(listener.getMethod().getName()).isEqualTo("onOrderCompleted");
        assertThat(listener.getReferenceTypes()).containsExactly(OrderCompleted.class);
    }

    @Modulith(additionalPackages = "org_springframework_modulith.architecture_fixture")
    public static class ArchitectureModulith {}
}
