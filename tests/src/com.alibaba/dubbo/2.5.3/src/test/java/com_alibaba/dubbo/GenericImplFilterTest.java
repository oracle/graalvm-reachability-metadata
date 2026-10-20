/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.filter.GenericImplFilter;
import com.alibaba.dubbo.rpc.service.GenericException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GenericImplFilterTest {
    @Test
    void realizesGenericValuesAndExceptions() {
        GenericImplFilter filter = new GenericImplFilter();
        Map<String, Object> person = new HashMap<String, Object>();
        person.put("name", "Ada");

        Result value = filter.invoke(new GenericInvoker(new RpcResult(person)), invocation());
        Result failure =
                filter.invoke(
                        new GenericInvoker(
                                new RpcResult(
                                        new GenericException(
                                                ConstructedFailure.class.getName(), "expected"))),
                        invocation());

        Result defaultConstructedFailure =
                filter.invoke(
                        new GenericInvoker(
                                new RpcResult(
                                        new GenericException(
                                                DefaultConstructedFailure.class.getName(),
                                                "default"))),
                        invocation());

        assertThat(((Person) value.getValue()).name).isEqualTo("Ada");
        assertThat(failure.getException()).isInstanceOf(ConstructedFailure.class);
        assertThat(defaultConstructedFailure.getException())
                .isInstanceOf(DefaultConstructedFailure.class);
    }

    private static RpcInvocation invocation() {
        return new RpcInvocation("person", new Class<?>[0], new Object[0]);
    }

    public interface GenericService {
        Person person();
    }

    public static class Person {
        public String name;

        public Person() { }
    }

    public static class ConstructedFailure extends RuntimeException {
        public ConstructedFailure(String message) {
            super(message);
        }
    }

    public static class DefaultConstructedFailure extends RuntimeException {
        public DefaultConstructedFailure() { }
    }

    private static class GenericInvoker implements Invoker<GenericService> {
        private final URL url =
                new URL(
                        "test",
                        "127.0.0.1",
                        0,
                        GenericService.class.getName(),
                        "generic",
                        "true");
        private final Result result;

        GenericInvoker(Result result) {
            this.result = result;
        }

        @Override
        public Class<GenericService> getInterface() {
            return GenericService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            assertThat(invocation.getMethodName()).isEqualTo("$invoke");
            return result;
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() { }
    }
}
