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
import com.alibaba.dubbo.rpc.cluster.Directory;
import com.alibaba.dubbo.rpc.cluster.support.MergeableClusterInvoker;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MergeableClusterInvokerTest {
    @Test
    void mergesResultsWithReturnMethodAndArrayMerger() {
        URL directoryUrl =
                new URL(
                        "test",
                        "127.0.0.1",
                        0,
                        MergeService.class.getName(),
                        "combine.merger",
                        ".merge",
                        "combine.timeout",
                        "10000",
                        "values.merger",
                        "default",
                        "values.timeout",
                        "10000");
        List<Invoker<MergeService>> invokers =
                Arrays.<Invoker<MergeService>>asList(
                        new ResultInvoker("one", new MergeValue("A"), new String[] {"A"}),
                        new ResultInvoker("two", new MergeValue("B"), new String[] {"B"}));
        MergeableClusterInvoker<MergeService> cluster =
                new MergeableClusterInvoker<MergeService>(
                        new FixedDirectory(directoryUrl, invokers));

        Result combined =
                cluster.invoke(new RpcInvocation("combine", new Class<?>[0], new Object[0]));
        Result values =
                cluster.invoke(new RpcInvocation("values", new Class<?>[0], new Object[0]));

        assertThat(((MergeValue) combined.getValue()).value).isIn("AB", "BA");
        assertThat((String[]) values.getValue()).containsExactlyInAnyOrder("A", "B");
        cluster.destroy();
    }

    public interface MergeService {
        MergeValue combine();

        String[] values();
    }

    public static class MergeValue {
        final String value;

        MergeValue(String value) {
            this.value = value;
        }

        public MergeValue merge(MergeValue other) {
            return new MergeValue(value + other.value);
        }
    }

    private static class ResultInvoker implements Invoker<MergeService> {
        private final URL url;
        private final MergeValue combined;
        private final String[] values;

        ResultInvoker(String group, MergeValue combined, String[] values) {
            this.url =
                    new URL(
                            "test",
                            "127.0.0.1",
                            0,
                            MergeService.class.getName(),
                            "group",
                            group);
            this.combined = combined;
            this.values = values;
        }

        @Override
        public Class<MergeService> getInterface() {
            return MergeService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            return new RpcResult("combine".equals(invocation.getMethodName()) ? combined : values);
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

    private static class FixedDirectory implements Directory<MergeService> {
        private final URL url;
        private final List<Invoker<MergeService>> invokers;

        FixedDirectory(URL url, List<Invoker<MergeService>> invokers) {
            this.url = url;
            this.invokers = invokers;
        }

        @Override
        public Class<MergeService> getInterface() {
            return MergeService.class;
        }

        @Override
        public List<Invoker<MergeService>> list(Invocation invocation) {
            return invokers;
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
        public void destroy() {
            for (Invoker<MergeService> invoker : invokers) {
                invoker.destroy();
            }
        }
    }
}
