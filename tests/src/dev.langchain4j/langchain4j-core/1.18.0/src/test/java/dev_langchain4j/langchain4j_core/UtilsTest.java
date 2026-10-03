/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.internal.Utils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    void discoversToolsDeclaredOnClassesAndDefaultInterfaceMethods() {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(new DeclaredTools());

        assertThat(specifications).extracting(ToolSpecification::name).containsExactly("declaredTool", "interfaceTool");
    }

    @Test
    void findsAnnotatedMethodDeclaredByProxyInterface() throws NoSuchMethodException {
        Object proxy = Proxy.newProxyInstance(
                UtilsTest.class.getClassLoader(),
                new Class<?>[] {ProxiedTools.class},
                new ProxyToolInvocationHandler());
        Method proxyMethod = proxy.getClass().getMethod("proxyTool");

        assertThat(Utils.getAnnotatedMethod(proxyMethod, Tool.class))
                .contains(ProxiedTools.class.getMethod("proxyTool"));
    }

    public interface ProxiedTools {

        @Tool
        String proxyTool();
    }

    private static final class ProxyToolInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "proxy";
        }
    }

    private static class DeclaredTools implements InheritedTools {

        @Tool
        public String declaredTool() {
            return "declared";
        }
    }

    private interface InheritedTools {

        @Tool
        default String interfaceTool() {
            return "interface";
        }
    }
}
