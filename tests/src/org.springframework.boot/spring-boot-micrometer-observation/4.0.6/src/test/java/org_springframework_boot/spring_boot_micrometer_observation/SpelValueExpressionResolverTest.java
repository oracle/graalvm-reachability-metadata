/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_observation;

import io.micrometer.common.annotation.ValueExpressionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpelValueExpressionResolverTest {
    @Test
    void resolvesObservationTagValuesUsingTheParameterAsExpressionRoot() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ObservationAutoConfiguration.class)) {
            ValueExpressionResolver resolver = context.getBean(ValueExpressionResolver.class);

            assertEquals("priority", resolver.resolve("#root['tier']", Map.of("tier", "priority")));
            assertEquals("42", resolver.resolve("#root + 2", 40));
            assertEquals("anonymous", resolver.resolve("#root ?: 'anonymous'", null));
        }
    }

    @Test
    void preservesAnApplicationProvidedValueExpressionResolver() {
        ValueExpressionResolver supplied = (expression, parameter) -> expression + ":" + parameter;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ValueExpressionResolver.class, () -> supplied);
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ValueExpressionResolver.class).size());
            ValueExpressionResolver resolver = context.getBean(ValueExpressionResolver.class);
            assertSame(supplied, resolver);
            assertEquals("tier:priority", resolver.resolve("tier", "priority"));
        }
    }
}
