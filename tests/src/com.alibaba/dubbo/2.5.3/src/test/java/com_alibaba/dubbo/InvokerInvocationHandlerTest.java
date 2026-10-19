/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.jupiter.api.Test;

public class InvokerInvocationHandlerTest {
    @Test
    void forwardsInterfaceCallsToTheInvoker() {
        RpcTestSupport.Echo proxy = new JdkProxyFactory().getProxy(RpcTestSupport.newInvoker());

        assertThat(proxy.echo("forwarded")).isEqualTo("echo:forwarded");
        assertThat(proxy.toString()).contains("test://127.0.0.1");
    }
}
