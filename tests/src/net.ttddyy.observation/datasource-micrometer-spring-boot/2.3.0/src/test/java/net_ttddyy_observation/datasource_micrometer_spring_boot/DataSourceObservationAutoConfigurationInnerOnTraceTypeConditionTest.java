/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer_spring_boot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationAutoConfiguration;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import net.ttddyy.observation.tracing.ResultSetTracingObservationHandler;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the `@ConditionalOnTraceType` condition that gates the tracing observation handlers on
/// the `jdbc.includes` property, both with the default trace types and with explicit ones.
public class DataSourceObservationAutoConfigurationInnerOnTraceTypeConditionTest {

    private static final List<Class<?>> TRACING_HANDLERS = List.of(
            ConnectionTracingObservationHandler.class,
            QueryTracingObservationHandler.class,
            ResultSetTracingObservationHandler.class);

    @Test
    void registersTracingHandlersForConfiguredTraceTypes() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceObservationAutoConfiguration.class))
                .withPropertyValues("management.tracing.export.enabled=true")
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .withBean(Tracer.class, () -> Tracer.NOOP);

        Map<String, Set<Class<?>>> handlersByIncludes = new LinkedHashMap<>();
        runner.run(context -> handlersByIncludes.put("defaults", registeredHandlers(context)));
        runner.withPropertyValues("jdbc.includes=query,fetch")
                .run(context -> handlersByIncludes.put("query,fetch", registeredHandlers(context)));
        runner.withPropertyValues("jdbc.includes=keys")
                .run(context -> handlersByIncludes.put("keys", registeredHandlers(context)));

        assertThat(handlersByIncludes.get("defaults")).containsExactlyInAnyOrderElementsOf(TRACING_HANDLERS);
        assertThat(handlersByIncludes.get("query,fetch"))
                .containsExactlyInAnyOrder(QueryTracingObservationHandler.class, ResultSetTracingObservationHandler.class);
        assertThat(handlersByIncludes.get("keys")).isEmpty();
    }

    private static Set<Class<?>> registeredHandlers(AssertableApplicationContext context) {
        assertThat(context.getStartupFailure()).isNull();
        return TRACING_HANDLERS.stream()
                .filter(type -> context.getBeanNamesForType(type).length == 1)
                .collect(Collectors.toSet());
    }
}
