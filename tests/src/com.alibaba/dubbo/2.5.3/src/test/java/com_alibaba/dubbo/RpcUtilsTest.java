/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

public class RpcUtilsTest {
    @Test
    void discoversRpcReturnTypesFromTheServiceContract() {
        Invoker<RpcTestSupport.Echo> invoker = RpcTestSupport.newInvoker();
        RpcInvocation invocation =
                new RpcInvocation("echo", new Class<?>[] {String.class}, new Object[] {"value"});
        invocation.setInvoker(invoker);

        assertThat(RpcUtils.getReturnType(invocation)).isEqualTo(String.class);
        assertThat(RpcUtils.getReturnTypes(invocation))
                .containsExactly(new Type[] {String.class, String.class});
    }
}
