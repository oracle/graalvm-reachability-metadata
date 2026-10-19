/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.spring.AnnotationBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class AnnotationBeanTest {
    @Test
    void scansConfiguredPackagesForDubboServices() {
        AnnotationBean annotations = new AnnotationBean();
        annotations.setPackage("com_alibaba.dubbo.no_services");
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        annotations.postProcessBeanFactory(beanFactory);
        annotations.setPackage("com_alibaba.dubbo");
        PlainBean bean = new PlainBean();

        assertThat(annotations.postProcessBeforeInitialization(bean, "plainBean")).isSameAs(bean);
        assertThat(beanFactory.getBeanDefinitionNames()).hasSize(2);
        assertThat(annotations.getPackage()).isEqualTo("com_alibaba.dubbo");
    }

    public static class PlainBean {
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
