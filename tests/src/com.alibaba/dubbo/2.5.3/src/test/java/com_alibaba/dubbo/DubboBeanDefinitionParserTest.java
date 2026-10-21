/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.ApplicationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboBeanDefinitionParserTest {
    @Test
    void parsesDubboApplicationConfiguration() {
        ClassPathXmlApplicationContext context =
                new ClassPathXmlApplicationContext("dubbo-parser-test.xml");
        try {
            ApplicationConfig application =
                    (ApplicationConfig) context.getBean("parserApplication");

            assertThat(application.getName()).isEqualTo("parser-test");
            assertThat(application.getOwner()).isEqualTo("test-owner");
        } finally {
            context.close();
        }
    }
}
