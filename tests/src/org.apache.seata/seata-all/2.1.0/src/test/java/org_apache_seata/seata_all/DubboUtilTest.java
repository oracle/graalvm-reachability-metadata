/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.integration.tx.api.util.DubboUtil;
import org.junit.jupiter.api.Test;

public class DubboUtilTest {
    @Test
    void extractsInterfaceFromDubboProxyHandlerChain() throws Exception {
        DubboProxyClient proxyClient = new DubboProxyClient(new Handler(new InvokerWrapper(
                new ClusterInvoker(ServiceApi.class))));

        Class<?> assistInterface = DubboUtil.getAssistInterface(proxyClient);

        assertThat(assistInterface).isEqualTo(ServiceApi.class);
    }

    @Test
    void ignoresObjectsThatDoNotUseDubboProxyNaming() throws Exception {
        Object ordinaryObject = new Object();

        Class<?> assistInterface = DubboUtil.getAssistInterface(ordinaryObject);

        assertThat(assistInterface).isNull();
    }

    interface ServiceApi {
    }

    static class DubboProxyClient {
        private final Handler handler;

        DubboProxyClient(Handler handler) {
            this.handler = handler;
        }
    }

    static class Handler {
        private final InvokerWrapper invoker;

        Handler(InvokerWrapper invoker) {
            this.invoker = invoker;
        }
    }

    static class InvokerWrapper {
        private final ClusterInvoker invoker;

        InvokerWrapper(ClusterInvoker invoker) {
            this.invoker = invoker;
        }
    }

    static class ClusterInvoker {
        private final Class<?> serviceInterface;

        ClusterInvoker(Class<?> serviceInterface) {
            this.serviceInterface = serviceInterface;
        }

        Class<?> getInterface() {
            return serviceInterface;
        }
    }
}
