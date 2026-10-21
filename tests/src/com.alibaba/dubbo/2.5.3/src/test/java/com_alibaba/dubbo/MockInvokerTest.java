/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.support.MockInvoker;
import org.junit.jupiter.api.Test;

public class MockInvokerTest {
    @Test
    void invokesConfiguredImplementationsAndConstructsConfiguredFailures() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();

        MockInvoker<Echo> implementation =
                new MockInvoker<Echo>(url(EchoMock.class.getName()));
        Result result = implementation.invoke(invocation());
        assertThat(result.getValue()).isEqualTo("mock:dubbo");

        MockInvoker<Echo> failure =
                new MockInvoker<Echo>(url("throw " + ExpectedFailure.class.getName()));
        assertThatThrownBy(() -> failure.invoke(invocation()))
                .hasCauseInstanceOf(ExpectedFailure.class)
                .hasRootCauseMessage(" mocked exception for Service degradation. ");
    }

    private static URL url(String mock) {
        return new URL(
                "mock", "127.0.0.1", 0, Echo.class.getName(), "mock", mock, "proxy", "jdk");
    }

    private static RpcInvocation invocation() {
        return new RpcInvocation("echo", new Class<?>[] {String.class}, new Object[] {"dubbo"});
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoMock implements Echo {
        public EchoMock() { }

        @Override
        public String echo(String value) {
            return "mock:" + value;
        }
    }

    public static class ExpectedFailure extends RuntimeException {
        public ExpectedFailure(String message) {
            super(message);
        }
    }
}
