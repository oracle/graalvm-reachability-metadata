/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

public class ServiceBeanTest {
    @Test
    void registersWithAndPublishesTheSpringContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        ServiceBean<Object> service = new ServiceBean<Object>();

        service.setApplicationContext(context);

        assertThat(ServiceBean.getSpringContext()).isSameAs(context);
    }
}
