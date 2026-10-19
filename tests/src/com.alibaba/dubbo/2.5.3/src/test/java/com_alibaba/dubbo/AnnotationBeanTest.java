/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.spring.AnnotationBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;

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

    @Test
    void injectsAReferenceIntoAnAnnotatedField() throws Exception {
        ReferenceConfigTest.installDirectReferenceProtocol();
        ApplicationConfig application = new ApplicationConfig("annotation-bean-test");
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("application", application);
        context.refresh();
        AnnotationBean annotations = new AnnotationBean();
        annotations.setApplicationContext(context);
        ClientBean client = new ClientBean();
        try {
            annotations.postProcessBeforeInitialization(client, "client");

            assertThat(client.echo.echo("field")).isEqualTo("echo:field");
        } finally {
            annotations.destroy();
            context.close();
        }
    }

    public static class ClientBean {
        @Reference(url = "reference-test://127.0.0.1", proxy = "reference-test")
        private ReferenceConfigTest.Echo echo;
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
