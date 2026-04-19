/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void instantiatingHttpServletSubclassLoadsLocalizedMessages() {
        assertThatCode(ExposedHttpServlet::new)
                .doesNotThrowAnyException();
    }

    @Test
    void doOptionsIncludesSubclassHttpMethodsInAllowHeader() throws Exception {
        final ExposedHttpServlet servlet = new ExposedHttpServlet();
        final RecordedResponse response = new RecordedResponse();

        servlet.invokeDoOptions(unusedRequest(), response.asHttpServletResponse());

        assertThat(response.headerName()).isEqualTo("Allow");
        assertThat(response.headerValue()).isEqualTo("POST, TRACE, OPTIONS");
    }

    private static HttpServletRequest unusedRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new UnsupportedInvocationHandler());
    }

    static final class ExposedHttpServlet extends HttpServlet {
        void invokeDoOptions(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            super.doOptions(request, response);
        }

        @Override
        protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
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

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments) {
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

    private static final class UnsupportedInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
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
