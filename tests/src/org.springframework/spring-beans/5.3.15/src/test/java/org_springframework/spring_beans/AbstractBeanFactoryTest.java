/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBeanFactoryTest {
    @Test
    void resolvesBeanClassNameThroughExpressionResolver() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setBeanExpressionResolver(new ClassNameExpressionResolver());
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setBeanClassName("resolvedBeanClass");
        factory.registerBeanDefinition("resolvedBean", definition);

        ResolvedBean bean = factory.getBean("resolvedBean", ResolvedBean.class);

        assertThat(bean.getValue()).isEqualTo("resolved");
    }

    public static class ClassNameExpressionResolver implements BeanExpressionResolver {
        @Override
        public Object evaluate(String value, BeanExpressionContext evalContext) {
            if ("resolvedBeanClass".equals(value)) {
                return ResolvedBean.class.getName();
            }
            return value;
        }
    }

    public static class ResolvedBean {
        public String getValue() {
            return "resolved";
        }
    }
}
