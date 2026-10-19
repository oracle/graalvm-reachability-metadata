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
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.StaticContext;
import com.alibaba.dubbo.rpc.protocol.dubbo.filter.FutureFilter;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class FutureFilterTest {
    @Test
    void invokesConfiguredCallbacksForSuccessAndFailure() throws Exception {
        URL url = new URL("test", "127.0.0.1", 0, CallbackService.class.getName());
        CallbackRecorder callbacks = new CallbackRecorder();
        register(url, Constants.ON_INVOKE_METHOD_KEY, Constants.ON_INVOKE_INSTANCE_KEY,
                "before", callbacks);
        register(url, Constants.ON_RETURN_METHOD_KEY, Constants.ON_RETURN_INSTANCE_KEY,
                "returned", callbacks);
        register(url, Constants.ON_THROW_METHOD_KEY, Constants.ON_THROW_INSTANCE_KEY,
                "thrown", callbacks);

        try {
            FutureFilter filter = new FutureFilter();
            RpcInvocation success = invocation("first");
            RpcInvocation failure = invocation("second");

            Result returned = filter.invoke(new CallbackInvoker(url, new RpcResult("done")), success);
            IllegalStateException expected = new IllegalStateException("expected");
            Result thrown = filter.invoke(new CallbackInvoker(url, new RpcResult(expected)), failure);

            assertThat(returned.getValue()).isEqualTo("done");
            assertThat(thrown.getException()).isSameAs(expected);
            assertThat(callbacks.before).containsExactly("first", "second");
            assertThat(callbacks.returned).isEqualTo("done:first");
            assertThat(callbacks.thrown).isEqualTo("expected:second");
        } finally {
            StaticContext.getSystemContext().clear();
        }
    }

    private static RpcInvocation invocation(String value) {
        return new RpcInvocation("call", new Class<?>[] {String.class}, new Object[] {value});
    }

    private static void register(
            URL url, String methodKey, String instanceKey, String methodName, Object instance)
            throws Exception {
        Method method =
                ReflectUtils.findMethodByMethodSignature(
                        CallbackRecorder.class, methodName, null);
        StaticContext context = StaticContext.getSystemContext();
        context.put(StaticContext.getKey(url, "call", methodKey), method);
        context.put(StaticContext.getKey(url, "call", instanceKey), instance);
    }

    public interface CallbackService {
        String call(String value);
    }

    public static class CallbackRecorder {
        private final java.util.List<String> before = new java.util.ArrayList<String>();
        private String returned;
        private String thrown;

        public void before(String value) {
            before.add(value);
        }

        public void returned(String result, String value) {
            returned = result + ":" + value;
        }

        public void thrown(Throwable failure, String value) {
            thrown = failure.getMessage() + ":" + value;
        }
    }

    private static class CallbackInvoker implements Invoker<CallbackService> {
        private final URL url;
        private final Result result;

        CallbackInvoker(URL url, Result result) {
            this.url = url;
            this.result = result;
        }

        @Override
        public Class<CallbackService> getInterface() {
            return CallbackService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
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
