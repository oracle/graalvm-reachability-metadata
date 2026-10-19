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
import com.alibaba.dubbo.rpc.filter.CompatibleFilter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CompatibleFilterTest {
    @Test
    void convertsJsonStyleResultToDeclaredReturnType() {
        Map<String, Object> value = new HashMap<String, Object>();
        value.put("name", "Dubbo");
        CompatibleFilter filter = new CompatibleFilter();

        Result result =
                filter.invoke(
                        new ValueInvoker(value),
                        new RpcInvocation("value", new Class<?>[0], new Object[0]));

        assertThat(((Value) result.getValue()).name).isEqualTo("Dubbo");
    }

    public interface ValueService {
        Value value();
    }

    public static class Value {
        public String name;

        public Value() {}
    }

    private static class ValueInvoker implements Invoker<ValueService> {
        private final URL url =
                new URL(
                        "test",
                        "127.0.0.1",
                        0,
                        ValueService.class.getName(),
                        "serialization",
                        "json");
        private final Object value;

        ValueInvoker(Object value) {
            this.value = value;
        }

        @Override
        public Class<ValueService> getInterface() {
            return ValueService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            return new RpcResult(value);
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
        public void destroy() {}
    }
}
