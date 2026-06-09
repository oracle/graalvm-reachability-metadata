/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public final class TestBeanOverrideHandlerTest__TestBeanScenario__ApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        GenericApplicationContext context = (GenericApplicationContext) applicationContext;

        context.registerBean(
                "greetingService",
                TestBeanOverrideHandlerTest.GreetingService.class,
                TestBeanOverrideHandlerTest.TestBeanScenario::createAotGreetingService);
        context.registerBean(
                "greetingConsumer",
                TestBeanOverrideHandlerTest.GreetingConsumer.class,
                () -> new TestBeanOverrideHandlerTest.GreetingConsumer(
                        context.getBean(TestBeanOverrideHandlerTest.GreetingService.class)));
    }
}
