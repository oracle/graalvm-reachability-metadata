/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultBeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;

public class MapperScannerBeanDefinitionParserTest {

    @Test
    void configuresScannerFromClassNamesInXml() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                    <mybatis:scan base-package="example.persistence"
                                  annotation="org.apache.ibatis.annotations.Mapper"
                                  marker-interface="org.apache.ibatis.session.SqlSession"
                                  name-generator="org.springframework.beans.factory.support.DefaultBeanNameGenerator"
                                  mapper-factory-bean-class="org.mybatis.spring.mapper.MapperFactoryBean"
                                  factory-ref="sessionFactory"
                                  template-ref="sessionTemplate"
                                  lazy-initialization="true"/>
                </beans>
                """;
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
        reader.setNamespaceAware(true);

        int count = reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(count).isEqualTo(1);
        assertThat(registry.getBeanDefinitionNames()).hasSize(1);
        BeanDefinition scanner = registry.getBeanDefinition(registry.getBeanDefinitionNames()[0]);
        assertThat(scanner.getBeanClassName()).isEqualTo(MapperScannerConfigurer.class.getName());
        assertThat(scanner.getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
        MutablePropertyValues properties = scanner.getPropertyValues();
        assertThat(properties.get("annotationClass")).isEqualTo(Mapper.class);
        assertThat(properties.get("markerInterface")).isEqualTo(SqlSession.class);
        assertThat(properties.get("mapperFactoryBeanClass")).isEqualTo(MapperFactoryBean.class);
        assertThat(properties.get("nameGenerator")).isInstanceOf(DefaultBeanNameGenerator.class);
        assertThat(properties.get("basePackage")).isEqualTo("example.persistence");
        assertThat(properties.get("sqlSessionFactoryBeanName")).isEqualTo("sessionFactory");
        assertThat(properties.get("sqlSessionTemplateBeanName")).isEqualTo("sessionTemplate");
        assertThat(properties.get("lazyInitialization")).isEqualTo("true");
        assertThat(properties.get("processPropertyPlaceHolders")).isEqualTo(true);
    }
}
