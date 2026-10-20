/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Repository;

public class MapperScannerBeanDefinitionParserTest {

    @Test
    void parsesAllClassValuedScannerAttributes() {
        GenericApplicationContext context = new GenericApplicationContext();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);

        int definitionCount = reader.loadBeanDefinitions(new ByteArrayResource(scannerXml(), "MyBatis scanner XML"));

        assertThat(definitionCount).isEqualTo(1);
        assertThat(context.getBeanFactory().getBeanDefinitionNames()).hasSize(1);
        BeanDefinition definition = context.getBeanFactory()
                .getBeanDefinition(context.getBeanFactory().getBeanDefinitionNames()[0]);
        assertThat(definition.getBeanClassName()).isEqualTo(MapperScannerConfigurer.class.getName());
        assertPropertyValue(definition, "annotationClass", Repository.class);
        assertPropertyValue(definition, "markerInterface", Serializable.class);
        assertPropertyValue(definition, "mapperFactoryBeanClass", MapperFactoryBean.class);
        assertThat(propertyValue(definition, "nameGenerator")).isInstanceOf(AnnotationBeanNameGenerator.class);
        assertThat(propertyValue(definition, "basePackage")).isEqualTo("org_mybatis.mybatis_spring.no_mappers");
    }

    private static void assertPropertyValue(BeanDefinition definition, String propertyName, Class<?> expected) {
        assertThat(propertyValue(definition, propertyName)).isSameAs(expected);
    }

    private static Object propertyValue(BeanDefinition definition, String propertyName) {
        PropertyValue propertyValue = definition.getPropertyValues().getPropertyValue(propertyName);
        assertThat(propertyValue).isNotNull();
        return propertyValue.getValue();
    }

    private static byte[] scannerXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                       xsi:schemaLocation="
                         http://www.springframework.org/schema/beans
                         http://www.springframework.org/schema/beans/spring-beans.xsd
                         http://mybatis.org/schema/mybatis-spring
                         http://mybatis.org/schema/mybatis-spring.xsd">
                    <mybatis:scan base-package="org_mybatis.mybatis_spring.no_mappers"
                                  annotation="org.springframework.stereotype.Repository"
                                  marker-interface="java.io.Serializable"
                                  name-generator="org.springframework.context.annotation.AnnotationBeanNameGenerator"
                                  mapper-factory-bean-class="org.mybatis.spring.mapper.MapperFactoryBean"/>
                </beans>
                """.getBytes(StandardCharsets.UTF_8);
    }
}
