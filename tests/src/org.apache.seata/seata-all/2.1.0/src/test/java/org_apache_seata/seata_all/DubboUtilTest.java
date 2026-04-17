/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.apache.seata.integration.tx.api.util.DubboUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DubboUtilTest {
    @Test
    void getAssistInterfaceUnwrapsDubboStyleInvokerChain() throws Exception {
        SyntheticDubboProxy proxy = new SyntheticDubboProxy(
                new InvokerInvocationHandler(new ClusterInvoker(new TerminalInvoker(SampleService.class))));

        assertThat(DubboUtil.getAssistInterface(proxy)).isSameAs(SampleService.class);
    }

    @Test
    void isDubboProxyNameRecognizesDubbo3StyleProxyNames() {
        assertThat(DubboUtil.isDubboProxyName(SyntheticDubboProxy.class.getName())).isTrue();
        assertThat(DubboUtil.isDubboProxyName(PlainProxy.class.getName())).isFalse();
    }

    private interface SampleService {
    }

    private static final class SyntheticDubboProxy {
        private final InvokerInvocationHandler handler;

        private SyntheticDubboProxy(InvokerInvocationHandler handler) {
            this.handler = handler;
        }
    }

    private static final class PlainProxy {
    }

    private static final class InvokerInvocationHandler {
        private final Object invoker;

        private InvokerInvocationHandler(Object invoker) {
            this.invoker = invoker;
        }
    }

    private static final class ClusterInvoker {
        private final Object invoker;

        private ClusterInvoker(Object invoker) {
            this.invoker = invoker;
        }
    }

    private static final class TerminalInvoker {
        private final Class<?> interfaceClass;

        private TerminalInvoker(Class<?> interfaceClass) {
            this.interfaceClass = interfaceClass;
        }

        public Class<?> getInterface() {
            return interfaceClass;
        }
    }
}
