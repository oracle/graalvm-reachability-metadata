/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class HttpServletTests {
    @Test
    void doOptionsIncludesSubclassHttpMethodsInAllowHeader() throws ServletException, IOException {
        ExposedHttpServlet servlet = new ExposedHttpServlet();
        RecordedResponse response = new RecordedResponse();

        servlet.invokeDoOptions(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.headerName()).isEqualTo("Allow");
        assertThat(response.headerValue()).isEqualTo("POST, TRACE, OPTIONS");
    }

    private static HttpServletRequest requestWithProtocol(String protocol) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new RequestInvocationHandler(protocol));
    }

    private static final class ExposedHttpServlet extends HttpServlet {
        void invokeDoOptions(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doOptions(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        }
    }

    private static final class RecordedResponse {
        private String headerName;
        private String headerValue;

        HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletTests.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
        }

        String headerName() {
            return headerName;
        }

        String headerValue() {
            return headerValue;
        }

        private Object handleInvocation(Object proxy, Method method, Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("setHeader") && arguments.length == 2) {
                headerName = (String) arguments[0];
                headerValue = (String) arguments[1];
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static final class RequestInvocationHandler implements InvocationHandler {
        private final String protocol;

        private RequestInvocationHandler(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("getProtocol")) {
                return protocol;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass().equals(Object.class);
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getName();
            default -> throw new UnsupportedOperationException(method.toGenericString());
        };
    }
}
