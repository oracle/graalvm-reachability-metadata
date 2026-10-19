/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.status.Status;
import com.alibaba.dubbo.config.spring.ServiceBean;
import com.alibaba.dubbo.config.spring.status.SpringStatusChecker;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringStatusCheckerTest {
    @Test
    void reportsLifecycleAndConfigurationLocations() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext();
        context.setConfigLocation("classpath:dubbo-parser-test.xml");
        new ServiceBean<Object>().setApplicationContext(context);

        Status status = new SpringStatusChecker().check();

        assertThat(status.getLevel()).isEqualTo(Status.Level.ERROR);
        assertThat(status.getMessage()).contains("dubbo-parser-test.xml");
    }
}
