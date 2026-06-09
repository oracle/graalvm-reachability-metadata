/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.test.context.aot;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org_springframework.spring_test.TestBeanOverrideHandlerTest__TestBeanScenario__ApplicationContextInitializer;

public final class AotTestContextInitializers__Generated {
    private static final String TEST_BEAN_SCENARIO =
            "org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanScenario";

    private AotTestContextInitializers__Generated() {
    }

    public static Map<String, Supplier<ApplicationContextInitializer<ConfigurableApplicationContext>>> getContextInitializers() {
        return Map.of(
                TEST_BEAN_SCENARIO,
                TestBeanOverrideHandlerTest__TestBeanScenario__ApplicationContextInitializer::new);
    }

    public static Map<String, Class<ApplicationContextInitializer<?>>> getContextInitializerClasses() {
        return Map.of(
                TEST_BEAN_SCENARIO,
                contextInitializerClass(
                        TestBeanOverrideHandlerTest__TestBeanScenario__ApplicationContextInitializer.class));
    }

    @SuppressWarnings("unchecked")
    private static Class<ApplicationContextInitializer<?>> contextInitializerClass(
            Class<? extends ApplicationContextInitializer<?>> initializerClass) {

        return (Class<ApplicationContextInitializer<?>>) initializerClass;
    }
}
