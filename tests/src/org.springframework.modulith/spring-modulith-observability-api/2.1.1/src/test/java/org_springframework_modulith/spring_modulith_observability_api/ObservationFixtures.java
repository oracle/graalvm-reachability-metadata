/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_observability_api;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Map;

import com.tngtech.archunit.core.domain.JavaClass;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.observability.ModulithContext;
import org.springframework.modulith.observability.ObservedModule;
import org.springframework.modulith.observability.ObservedModuleType;

final class ObservationFixtures {

    private ObservationFixtures() {
    }

    static ModulithContext context(boolean eventListener) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-application",
                Map.of("spring.application.name", "warehouse")));
        return new ModulithContext(new InventoryModule(eventListener), new ReservationInvocation(), environment);
    }

    public static class InventoryService {
        public String reserve() {
            return "reserved";
        }
    }

    private record ReservationInvocation(InventoryService service) implements MethodInvocation {
        ReservationInvocation() {
            this(new InventoryService());
        }

        @Override
        public Method getMethod() {
            try {
                return InventoryService.class.getMethod("reserve");
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }

        @Override
        public Object proceed() {
            return service.reserve();
        }

        @Override
        public Object getThis() {
            return service;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return getMethod();
        }
    }

    private record InventoryModule(boolean eventListener) implements ObservedModule {
        @Override
        public ApplicationModuleIdentifier getIdentifier() {
            return ApplicationModuleIdentifier.of("inventory");
        }

        @Override
        public String getDisplayName() {
            return "Inventory";
        }

        @Override
        public String getInvokedMethod(MethodInvocation invocation) {
            return invocation.getMethod().getName();
        }

        @Override
        public String format(MethodInvocation invocation) {
            return "InventoryService." + getInvokedMethod(invocation) + "()";
        }

        @Override
        public boolean exposes(JavaClass type) {
            return type.isEquivalentTo(InventoryService.class);
        }

        @Override
        public boolean isObservedModule(ApplicationModule module) {
            return getIdentifier().equals(module.getIdentifier());
        }

        @Override
        public ObservedModuleType getObservedModuleType(Class<?> type, ApplicationModules modules) {
            ApplicationModule module = modules.getModuleByType(type).orElseThrow();
            return new ObservedModuleType(modules, this, module.getArchitecturallyEvidentType(type));
        }

        @Override
        public boolean isEventListenerInvocation(MethodInvocation invocation) {
            return eventListener;
        }
    }
}
