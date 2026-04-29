/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import org.apache.seata.common.loader.LoadLevel;
import org.apache.seata.common.loader.Scope;
import org.apache.seata.integration.tx.api.interceptor.InvocationWrapper;
import org.apache.seata.integration.tx.api.interceptor.SeataInterceptorPosition;
import org.apache.seata.integration.tx.api.interceptor.handler.ProxyInvocationHandler;
import org.apache.seata.integration.tx.api.interceptor.parser.IfNeedEnhanceBean;
import org.apache.seata.integration.tx.api.interceptor.parser.InterfaceParser;
import org.apache.seata.integration.tx.api.util.ProxyUtil;
import org.junit.jupiter.api.Test;

public class ProxyUtilTest {
    @Test
    void createsSubclassProxyForTransactionalService() {
        TransactionalService target = new TransactionalService("original-message");

        TransactionalService proxy = ProxyUtil.createProxy(target, "transactionalService");

        assertThat(proxy).isNotSameAs(target);
        assertThat(proxy).isInstanceOf(TransactionalService.class);
        assertThat(proxy.getClass()).isNotEqualTo(TransactionalService.class);
    }

    @Test
    void reusesPreviouslyCreatedProxyForSameTarget() {
        TransactionalService target = new TransactionalService("cached-message");

        TransactionalService proxy = ProxyUtil.createProxy(target, "cachedService");

        assertThat(ProxyUtil.createProxy(target, "sameCachedService")).isSameAs(proxy);
    }

    public static class TransactionalService {
        private String message;

        public TransactionalService() {
            this("default-message");
        }

        public TransactionalService(String message) {
            this.message = message;
        }

        public String readMessage() {
            return message;
        }

        public void updateMessage(String newMessage) {
            this.message = newMessage;
        }
    }

    @LoadLevel(name = "proxy-util", scope = Scope.PROTOTYPE)
    public static class ProxyTargetInterfaceParser implements InterfaceParser {
        @Override
        public ProxyInvocationHandler parserInterfaceToProxy(Object target, String objectName) {
            if (target instanceof TransactionalService) {
                return new ProceedingProxyInvocationHandler();
            }
            return null;
        }

        @Override
        public IfNeedEnhanceBean parseIfNeedEnhancement(Class<?> beanClass) {
            IfNeedEnhanceBean ifNeedEnhanceBean = new IfNeedEnhanceBean();
            ifNeedEnhanceBean.setIfNeed(TransactionalService.class.isAssignableFrom(beanClass));
            return ifNeedEnhanceBean;
        }
    }

    public static class ProceedingProxyInvocationHandler implements ProxyInvocationHandler {
        @Override
        public Set<String> getMethodsToProxy() {
            return Collections.emptySet();
        }

        @Override
        public Object invoke(InvocationWrapper invocation) throws Throwable {
            return invocation.proceed();
        }

        @Override
        public SeataInterceptorPosition getPosition() {
            return SeataInterceptorPosition.Any;
        }

        @Override
        public String type() {
            return "proxy-util-test";
        }

        @Override
        public void setNextProxyInvocationHandler(ProxyInvocationHandler next) {
        }
    }
}
