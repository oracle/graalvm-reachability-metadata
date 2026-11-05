/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpringTest {
    @Test
    void managesConfiguredApplicationBean() {
        GenericApplicationContext context = new GenericApplicationContext();
        RootBeanDefinition definition = new RootBeanDefinition(TestBeans.Person.class);
        definition.getPropertyValues().addPropertyValue("name", "application");
        context.registerBeanDefinition("person", definition);
        try {
            context.refresh();
            TestBeans.Person person = (TestBeans.Person) context.getBean("person");
            assertEquals("application", person.getName());
            assertSame(person, context.getBean("person"));
        } finally {
            context.close();
        }
    }
}
