/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_converter;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;

@SuppressWarnings("deprecation")
final class ConverterCustomizerContext {
    private ConverterCustomizerContext() {
    }

    // Register the library's factory directly, without runtime configuration-class scanning.
    static GenericApplicationContext create(String factoryMethod, Class<?> customizerType,
            HttpMessageConverters legacy, HttpMessageConverter<?>... converters) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("converterConfiguration",
                new HttpMessageConvertersAutoConfiguration());
        if (legacy != null) {
            context.getBeanFactory().registerSingleton("legacyConverters", legacy);
        }
        for (int i = 0; i < converters.length; i++) {
            context.getBeanFactory().registerSingleton("converter" + i, converters[i]);
        }
        RootBeanDefinition definition = new RootBeanDefinition(customizerType);
        definition.setFactoryBeanName("converterConfiguration");
        definition.setFactoryMethodName(factoryMethod);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        context.registerBeanDefinition(factoryMethod, definition);
        context.refresh();
        return context;
    }
}
