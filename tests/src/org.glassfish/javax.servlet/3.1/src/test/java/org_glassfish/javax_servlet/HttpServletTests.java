/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

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

public class HttpServletTests {
    @Test
    void doGetReportsUnsupportedMethodWithLocalizedMessage() throws ServletException, IOException {
        ExposedHttpServlet servlet = new ExposedHttpServlet();
        RecordedResponse response = new RecordedResponse();

        servlet.invokeDoGet(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.message()).isEqualTo("HTTP method GET is not supported by this URL");
    }

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

    static final class ExposedHttpServlet extends HttpServlet {
        void invokeDoGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doGet(request, response);
        }

        void invokeDoOptions(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doOptions(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        }
    }

    static final class RecordedResponse {
        private int statusCode;
        private String message;
        private String headerName;
        private String headerValue;

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
            if (method.getName().equals("sendError") && arguments.length == 2) {
                statusCode = (Integer) arguments[0];
                message = (String) arguments[1];
                return null;
            }
            if (method.getName().equals("setHeader") && arguments.length == 2) {
                headerName = (String) arguments[0];
                headerValue = (String) arguments[1];
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    static final class RequestInvocationHandler implements InvocationHandler {
        private final String protocol;

        RequestInvocationHandler(String protocol) {
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
