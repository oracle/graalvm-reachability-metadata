/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.lang.reflect.Type;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionContextUtilsTest {
    @Test
    void findTypeResolvesBeanClassNameThroughDefaultClassLoader() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClassName(FunctionContextUtilsFunction.class.getName());
        beanFactory.registerBeanDefinition("uppercaseFunction", beanDefinition);

        Type functionType = FunctionContextUtils.findType(beanFactory, "uppercaseFunction");

        assertThat(functionType).isNotNull();
        assertThat(functionType.getTypeName()).contains(Function.class.getName());
    }

    @Test
    void getParamTypesFromBeanDefinitionFactoryUsesPublicFactoryMethods() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setNonPublicAccessAllowed(false);

        Class<?>[] parameterTypes = FunctionContextUtils.getParamTypesFromBeanDefinitionFactory(
                FunctionContextUtilsFactory.class, beanDefinition, "uppercaseFunction");

        assertThat(parameterTypes).isEmpty();
    }
}

class FunctionContextUtilsFunction implements Function<String, String> {
    @Override
    public String apply(String value) {
        return value.toUpperCase();
    }
}

class FunctionContextUtilsFactory {
    @Bean
    public Function<String, String> uppercaseFunction() {
        return new FunctionContextUtilsFunction();
    }
}
