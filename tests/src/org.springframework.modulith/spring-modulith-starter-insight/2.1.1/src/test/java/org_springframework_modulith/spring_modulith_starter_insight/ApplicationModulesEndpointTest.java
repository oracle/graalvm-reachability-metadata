/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_starter_insight;

import java.util.Collection;
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

    @Test
    void exposesPrecomputedModuleStructureThroughActuator() {
        String structure = """
                {"orders":{"displayName":"Orders","basePackage":"example.orders","dependencies":[]}}
                """;
        AtomicInteger computations = new AtomicInteger();
        ApplicationModulesEndpoint endpoint = ApplicationModulesEndpoint.precomputed(() -> {
            computations.incrementAndGet();
            return structure;
        });
        assertThat(computations.get()).isZero();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("modulithEndpoint", ApplicationModulesEndpoint.class, () -> endpoint);
            context.refresh();

            WebEndpointDiscoverer discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(), EndpointMediaTypes.DEFAULT,
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Collection<ExposableWebEndpoint> endpoints = discoverer.getEndpoints();
            assertThat(endpoints).hasSize(1);
            ExposableWebEndpoint exposed = endpoints.iterator().next();
            assertThat(exposed.getEndpointId().toString()).isEqualTo("modulith");
            assertThat(exposed.getRootPath()).isEqualTo("modulith");
            assertThat(exposed.getOperations()).hasSize(1);

            WebOperation read = exposed.getOperations().iterator().next();
            assertThat(read.getType()).isEqualTo(OperationType.READ);
            InvocationContext invocation = new InvocationContext(SecurityContext.NONE, Map.of());
            assertThat(read.invoke(invocation)).isEqualTo(structure);
            assertThat(read.invoke(invocation)).isEqualTo(structure);
            assertThat(computations.get()).isEqualTo(1);
        }
    }

    @Test
    void initializesPrecomputedStructureOnlyOnce() {
        AtomicInteger computations = new AtomicInteger();
        ApplicationModulesEndpoint endpoint = ApplicationModulesEndpoint.precomputed(() -> {
            computations.incrementAndGet();
            return "{}";
        });
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        endpoint.initialize(beanFactory);
        endpoint.initialize(beanFactory);

        assertThat(computations.get()).isEqualTo(1);
    }

    @Test
    void rejectsMissingStructureSuppliers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationModulesEndpoint.precomputed(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationModulesEndpoint.ofApplicationModules(null));
    }
}
