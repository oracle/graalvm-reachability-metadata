/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.test.context.aot;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import org_springframework.spring_test.TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_ApplicationContextInitializer;

public final class AotTestContextInitializers__Generated {
    private AotTestContextInitializers__Generated() {
    }

    public static Map<String, Supplier<ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> getContextInitializers() {
        Map<String, Supplier<ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> map = new HashMap<>();
        map.put("org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanTestCase",
                TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_ApplicationContextInitializer::new);
        return map;
    }

    public static Map<String, Class<? extends ApplicationContextInitializer<?>>> getContextInitializerClasses() {
        Map<String, Class<? extends ApplicationContextInitializer<?>>> map = new HashMap<>();
        map.put("org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanTestCase",
                TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_ApplicationContextInitializer.class);
        return map;
    }
}
