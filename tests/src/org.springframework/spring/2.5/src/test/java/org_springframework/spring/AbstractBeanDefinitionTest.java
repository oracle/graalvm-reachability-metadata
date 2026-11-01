/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractBeanDefinitionTest {
    public static class RequiredConstructor {
        public RequiredConstructor(String value) {
        }
    }

    @Test
    void autodetectsSetterOrConstructorAutowiringFromPublicConstructors() {
        RootBeanDefinition setters = new RootBeanDefinition(TestBeans.Person.class);
        setters.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_AUTODETECT);
        assertEquals(AbstractBeanDefinition.AUTOWIRE_BY_TYPE, setters.getResolvedAutowireMode());
        RootBeanDefinition constructor = new RootBeanDefinition(RequiredConstructor.class);
        constructor.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_AUTODETECT);
        assertEquals(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR, constructor.getResolvedAutowireMode());
    }
}
