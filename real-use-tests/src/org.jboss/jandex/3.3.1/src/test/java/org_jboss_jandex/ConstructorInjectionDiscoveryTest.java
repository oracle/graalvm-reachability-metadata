/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Jandex's constructor metadata subsystem to build an application dependency-injection plan.
public class ConstructorInjectionDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversInjectableConstructorDependencies() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(ShipmentProcessor.class);
        indexer.indexClass(InventoryService.class);
        indexer.indexClass(PricingService.class);
        indexer.indexClass(Inject.class);
        indexer.indexClass(Dependency.class);
        Index index = indexer.complete();

        ClassInfo processor = index.getClassByName(DotName.createSimple(ShipmentProcessor.class));
        ConstructorPlan plan = ConstructorPlan.from(processor);

        assertEquals(ShipmentProcessor.class.getName(), plan.componentName());
        assertEquals(List.of(
                new Dependency("inventory", InventoryService.class.getName()),
                new Dependency("pricing", PricingService.class.getName())), plan.dependencies());
        assertNotNull(processor.constructors().get(0).annotation(DotName.createSimple(Inject.class)));
    }

    private record ConstructorPlan(String componentName, List<Dependency> dependencies) {
        private static ConstructorPlan from(ClassInfo component) {
            MethodInfo constructor = component.constructors().stream()
                    .filter(method -> method.hasAnnotation(DotName.createSimple(Inject.class)))
                    .findFirst()
                    .orElseThrow();
            return new ConstructorPlan(component.name().toString(), constructor.parameters().stream()
                    .map(ConstructorInjectionDiscoveryTest::dependency)
                    .toList());
        }
    }

    private record Dependency(String name, String type) {
    }

    private static Dependency dependency(MethodParameterInfo parameter) {
        return new Dependency(
                parameter.annotation(DotName.createSimple(DependencyMarker.class)).value().asString(),
                parameter.type().name().toString());
    }

    private static final class ShipmentProcessor {
        @Inject
        private ShipmentProcessor(
                @DependencyMarker("inventory") InventoryService inventory,
                @DependencyMarker("pricing") PricingService pricing) {
        }
    }

    private static final class InventoryService {
    }

    private static final class PricingService {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.CONSTRUCTOR)
    private @interface Inject {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface DependencyMarker {
        String value();
    }
}
