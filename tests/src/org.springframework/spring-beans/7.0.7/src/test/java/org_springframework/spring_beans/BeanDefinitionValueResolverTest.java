/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BeanDefinitionValueResolverTest {
    @Test
    void resolvesReferencesWithinManagedArrays() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("primary", "first");
        ManagedArray values = new ManagedArray(String.class.getName(), 2);
        values.add(new RuntimeBeanReference("primary"));
        values.add("second");
        RootBeanDefinition definition = new RootBeanDefinition(Pipeline.class);
        definition.getPropertyValues().add("stages", values);
        factory.registerBeanDefinition("pipeline", definition);
        Pipeline pipeline = factory.getBean("pipeline", Pipeline.class);
        assertArrayEquals(new String[] {"first", "second"}, pipeline.stages);
        assertSame(factory.getBean("primary"), pipeline.stages[0]);
    }

    public static class Pipeline {
        private String[] stages;

        public void setStages(String[] stages) {
            this.stages = stages;
        }
    }
}
