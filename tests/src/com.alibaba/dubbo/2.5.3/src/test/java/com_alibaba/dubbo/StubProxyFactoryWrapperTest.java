/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import com.alibaba.dubbo.rpc.proxy.wrapper.StubProxyFactoryWrapper;
import org.junit.jupiter.api.Test;

public class StubProxyFactoryWrapperTest {
    @Test
    void wrapsAServiceProxyWithItsConfiguredClientStub() {
        URL url =
                new URL("test", "127.0.0.1", 0, Echo.class.getName())
                        .addParameter(Constants.STUB_KEY, PrefixingStub.class.getName())
                        .addParameter(Constants.STUB_EVENT_KEY, "false");
        JdkProxyFactory jdk = new JdkProxyFactory();
        Invoker<Echo> invoker = jdk.getInvoker(new EchoService(), Echo.class, url);
        StubProxyFactoryWrapper wrapper = new StubProxyFactoryWrapper(jdk);

        Echo proxy = wrapper.getProxy(invoker);

        assertThat(proxy.echo("hello")).isEqualTo("stub:service:hello");
        invoker.destroy();
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoService implements Echo {
        @Override
        public String echo(String value) {
            return "service:" + value;
        }
    }

    public static class PrefixingStub implements Echo {
        private final Echo delegate;

        public PrefixingStub(Echo delegate) {
            this.delegate = delegate;
        }

        @Override
        public String echo(String value) {
            return "stub:" + delegate.echo(value);
        }
    }
}
