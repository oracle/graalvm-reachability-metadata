/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

class HttpServletTests {
    @Test
    void doGetReportsUnsupportedMethodWithLocalizedMessage() throws ServletException, IOException {
        final ExposedHttpServlet servlet = new ExposedHttpServlet();
        final ErrorResponse response = new ErrorResponse();

        servlet.invokeDoGet(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.message()).isEqualTo("HTTP method GET is not supported by this URL");
    }

    private static HttpServletRequest requestWithProtocol(final String protocol) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new RequestInvocationHandler(protocol));
    }

    private static final class ExposedHttpServlet extends HttpServlet {
        void invokeDoGet(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            super.doGet(request, response);
        }
    }

    private static final class ErrorResponse {
        private int statusCode;
        private String message;

        HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletTests.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
        }

        int statusCode() {
            return statusCode;
        }

        String message() {
            return message;
        }

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("sendError") && arguments.length == 2) {
                statusCode = (Integer) arguments[0];
                message = (String) arguments[1];
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static final class RequestInvocationHandler implements InvocationHandler {
        private final String protocol;

        private RequestInvocationHandler(final String protocol) {
            this.protocol = protocol;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("getProtocol")) {
                return protocol;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass().equals(Object.class);
    }

    private static Object handleObjectMethod(final Object proxy, final Method method, final Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getName();
            default -> throw new UnsupportedOperationException(method.toGenericString());
        };
    }
}
