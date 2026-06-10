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

import org.springframework.aot.generate.Generated;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org_springframework.spring_test.TestBeanOverrideHandlerTest_FactoryMethodTestCase__TestContext001_ApplicationContextInitializer;
import org_springframework.spring_test.TestClassScannerTest__TestContext002_ApplicationContextInitializer;

/**
 * Generated mappings for {@link AotTestContextInitializers}.
 */
@Generated
public final class AotTestContextInitializers__Generated {
    private AotTestContextInitializers__Generated() {
    }

    public static Map<String, Supplier<ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> getContextInitializers() {
        Map<String, Supplier<ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> map = new HashMap<>();
        map.put("org_springframework.spring_test.TestBeanOverrideHandlerTest$FactoryMethodTestCase",
                () -> new TestBeanOverrideHandlerTest_FactoryMethodTestCase__TestContext001_ApplicationContextInitializer());
        map.put("org_springframework.spring_test.TestClassScannerTest",
                () -> new TestClassScannerTest__TestContext002_ApplicationContextInitializer());
        map.put("org_springframework.spring_test.TestClassScannerTest$NestedSpringTestCase",
                () -> new TestClassScannerTest__TestContext002_ApplicationContextInitializer());
        map.put("org_springframework.spring_test.TestClassScannerTest$NestedSpringTestCase$DeepNestedSpringTestCase",
                () -> new TestClassScannerTest__TestContext002_ApplicationContextInitializer());
        return map;
    }

    public static Map<String, Class<? extends ApplicationContextInitializer<?>>> getContextInitializerClasses() {
        Map<String, Class<? extends ApplicationContextInitializer<?>>> map = new HashMap<>();
        map.put("org_springframework.spring_test.TestBeanOverrideHandlerTest$FactoryMethodTestCase",
                TestBeanOverrideHandlerTest_FactoryMethodTestCase__TestContext001_ApplicationContextInitializer.class);
        map.put("org_springframework.spring_test.TestClassScannerTest",
                TestClassScannerTest__TestContext002_ApplicationContextInitializer.class);
        map.put("org_springframework.spring_test.TestClassScannerTest$NestedSpringTestCase",
                TestClassScannerTest__TestContext002_ApplicationContextInitializer.class);
        map.put("org_springframework.spring_test.TestClassScannerTest$NestedSpringTestCase$DeepNestedSpringTestCase",
                TestClassScannerTest__TestContext002_ApplicationContextInitializer.class);
        return map;
    }
}
