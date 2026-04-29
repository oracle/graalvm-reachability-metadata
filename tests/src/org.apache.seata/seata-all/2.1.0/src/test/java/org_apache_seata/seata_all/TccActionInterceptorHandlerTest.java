/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collections;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.integration.tx.api.interceptor.DefaultInvocationWrapper;
import org.apache.seata.rm.tcc.interceptor.TccActionInterceptorHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TccActionInterceptorHandlerTest {
    @AfterEach
    void clearRootContext() {
        RootContext.unbindBranchType();
        RootContext.unbind();
    }

    @Test
    void invokesPlainTargetMethodAfterCheckingImplementedInterfacesForTccAnnotations() throws Throwable {
        PlainTccAction action = new PlainTccAction();
        TccActionInterceptorHandler handler = new TccActionInterceptorHandler(action, Collections.singleton("prepare"));
        Method method = PlainTccAction.class.getMethod("prepare", String.class);
        DefaultInvocationWrapper wrapper = new DefaultInvocationWrapper(
                action, action, method, new Object[] {"order-1"});

        RootContext.bind("127.0.0.1:8091:1");
        Object result = handler.invoke(wrapper);

        assertThat(result).isEqualTo("prepared-order-1");
        assertThat(action.getPreparedBusinessKey()).isEqualTo("order-1");
    }

    public interface PlainTccContract {
        String prepare(String businessKey);
    }

    public static class PlainTccAction implements PlainTccContract {
        private String preparedBusinessKey;

        @Override
        public String prepare(String businessKey) {
            preparedBusinessKey = businessKey;
            return "prepared-" + businessKey;
        }

        public String getPreparedBusinessKey() {
            return preparedBusinessKey;
        }
    }
}
