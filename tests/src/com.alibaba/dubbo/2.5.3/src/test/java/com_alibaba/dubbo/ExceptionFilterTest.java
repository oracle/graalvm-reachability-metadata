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
import com.alibaba.dubbo.rpc.filter.ExceptionFilter;
import org.junit.jupiter.api.Test;

public class ExceptionFilterTest {
    @Test
    void preservesDeclaredServiceException() {
        DeclaredFailure failure = new DeclaredFailure("declared");
        ExceptionFilter filter = new ExceptionFilter();

        Result result =
                filter.invoke(
                        new FailureInvoker(failure),
                        new RpcInvocation("call", new Class<?>[0], new Object[0]));

        assertThat(result.getException()).isSameAs(failure);
    }

    public interface FailureService {
        String call() throws DeclaredFailure;
    }

    public static class DeclaredFailure extends RuntimeException {
        public DeclaredFailure(String message) {
            super(message);
        }
    }

    private static class FailureInvoker implements Invoker<FailureService> {
        private final URL url =
                new URL("test", "127.0.0.1", 0, FailureService.class.getName());
        private final Throwable failure;

        FailureInvoker(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public Class<FailureService> getInterface() {
            return FailureService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            return new RpcResult(failure);
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
