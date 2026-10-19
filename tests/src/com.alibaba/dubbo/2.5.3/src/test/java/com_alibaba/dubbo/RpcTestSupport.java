/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory;

final class RpcTestSupport {
    private RpcTestSupport() {}

    static Invoker<Echo> newInvoker() {
        return new JdkProxyFactory()
                .getInvoker(
                        new EchoService(),
                        Echo.class,
                        new URL("test", "127.0.0.1", 0, Echo.class.getName()));
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoService implements Echo {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }
}
