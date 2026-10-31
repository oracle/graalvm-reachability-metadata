/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_actuator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.modulith.actuator.ApplicationModulesEndpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ApplicationModulesEndpointTest {

    private static final String MODULE_STRUCTURE = """
            {"inventory":{"displayName":"Inventory","basePackage":"shop.inventory","dependencies":[]},
             "checkout":{"displayName":"Checkout","basePackage":"shop.checkout",
                         "dependencies":[{"target":"inventory","types":["DEFAULT"]}]}}
            """;

    @Test
    void readsModuleStructureThroughActuatorAndCachesIt() {
        AtomicInteger loads = new AtomicInteger();
        ApplicationModulesEndpoint endpoint = ApplicationModulesEndpoint.precomputed(() -> {
            loads.incrementAndGet();
            return MODULE_STRUCTURE;
        });
        assertThat(loads.get()).isZero();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("moduleStructure", ApplicationModulesEndpoint.class, () -> endpoint);
            context.refresh();

            WebEndpointDiscoverer discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(), EndpointMediaTypes.DEFAULT,
                    List.of(), List.of(), List.of(), List.of(), List.of());
            assertThat(discoverer.getEndpoints()).hasSize(1);
            ExposableWebEndpoint exposed = discoverer.getEndpoints().iterator().next();
            assertThat(exposed.getEndpointId().toString()).isEqualTo("modulith");
            assertThat(exposed.getRootPath()).isEqualTo("modulith");
            assertThat(exposed.getOperations()).hasSize(1);
            WebOperation operation = exposed.getOperations().iterator().next();
            assertThat(operation.getType()).isEqualTo(OperationType.READ);

            InvocationContext invocation = new InvocationContext(SecurityContext.NONE, Map.of());
            assertThat(operation.invoke(invocation)).isEqualTo(MODULE_STRUCTURE);
            assertThat(operation.invoke(invocation)).isEqualTo(MODULE_STRUCTURE);
            endpoint.initialize(context.getBeanFactory());
            assertThat(loads.get()).isEqualTo(1);
        }
    }

    @Test
    void eagerlyLoadsStructureDuringBeanFactoryInitialization() {
        AtomicInteger loads = new AtomicInteger();
        ApplicationModulesEndpoint endpoint = ApplicationModulesEndpoint.precomputed(() -> {
            loads.incrementAndGet();
            return MODULE_STRUCTURE;
        });
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        assertThat(loads.get()).isZero();

        endpoint.initialize(beanFactory);
        assertThat(loads.get()).isEqualTo(1);
        endpoint.initialize(beanFactory);
        assertThat(loads.get()).isEqualTo(1);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("moduleStructure", ApplicationModulesEndpoint.class, () -> endpoint);
            context.refresh();
            WebEndpointDiscoverer discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(), EndpointMediaTypes.DEFAULT,
                    List.of(), List.of(), List.of(), List.of(), List.of());
            assertThat(discoverer.getEndpoints()).hasSize(1);
            ExposableWebEndpoint exposed = discoverer.getEndpoints().iterator().next();
            assertThat(exposed.getOperations()).hasSize(1);
            WebOperation operation = exposed.getOperations().iterator().next();
            assertThat(operation.invoke(new InvocationContext(SecurityContext.NONE, Map.of())))
                    .isEqualTo(MODULE_STRUCTURE);
            assertThat(loads.get()).isEqualTo(1);
        }
    }

    @Test
    void requiresSuppliersForBothEndpointFactories() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationModulesEndpoint.precomputed(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationModulesEndpoint.ofApplicationModules(null));
    }
}
