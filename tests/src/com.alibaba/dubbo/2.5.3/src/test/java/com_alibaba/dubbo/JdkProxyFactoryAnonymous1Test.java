/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;

public class JdkProxyFactoryAnonymous1Test {
    @Test
    void invokesTheServiceMethodByItsRpcSignature() {
        Invoker<RpcTestSupport.Echo> invoker = RpcTestSupport.newInvoker();
        RpcInvocation invocation =
                new RpcInvocation("echo", new Class<?>[] {String.class}, new Object[] {"direct"});

        Result result = invoker.invoke(invocation);

        assertThat(result.hasException()).isFalse();
        assertThat(result.getValue()).isEqualTo("echo:direct");
    }
}
