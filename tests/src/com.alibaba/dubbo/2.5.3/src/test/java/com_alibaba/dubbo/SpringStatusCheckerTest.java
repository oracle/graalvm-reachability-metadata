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
import org.springframework.context.support.GenericApplicationContext;

public class SpringStatusCheckerTest {
    @Test
    void reportsLifecycleAndConfigurationLocations() {
        ConfiguredApplicationContext context = new ConfiguredApplicationContext();
        context.refresh();
        context.start();
        ServiceBean<Object> service = new ServiceBean<Object>();
        service.setApplicationContext(context);

        try {
            Status status = new SpringStatusChecker().check();

            assertThat(status.getLevel()).isEqualTo(Status.Level.OK);
            assertThat(status.getMessage()).isEqualTo("classpath:first.xml,classpath:second.xml");
        } finally {
            context.close();
        }
    }

    public static class ConfiguredApplicationContext extends GenericApplicationContext {
        public String[] getConfigLocations() {
            return new String[] {"classpath:first.xml", "classpath:second.xml"};
        }
    }
}
