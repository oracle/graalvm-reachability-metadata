/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.test.context.aot;

import java.util.Map;
import java.util.function.Supplier;

import org_springframework.spring_test.TestBeanOverrideHandlerTest;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class AotTestContextInitializers__Generated {
    private AotTestContextInitializers__Generated() {
    }

    public static Map<String, Supplier<ApplicationContextInitializer<ConfigurableApplicationContext>>> getContextInitializers() {
        return Map.of(TestBeanOverrideHandlerTest.class.getName(),
                TestBeanOverrideHandlerTest.NoOpContextInitializer::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Map<String, Class<ApplicationContextInitializer<?>>> getContextInitializerClasses() {
        return Map.of(TestBeanOverrideHandlerTest.class.getName(),
                (Class) TestBeanOverrideHandlerTest.NoOpContextInitializer.class);
    }
}
