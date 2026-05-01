/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class NoBodyOutputStreamTest {
    @Test
    void headRequestInitializesNoBodyOutputStreamAndCountsOutputBytes() throws ServletException, IOException {
        final OutputStreamHeadServlet servlet = new OutputStreamHeadServlet();
        final ServletRequest request = requestWithMethod("HEAD");
        final RecordedHeadResponse response = new RecordedHeadResponse();
        final ServletResponse servletResponse = response.asHttpServletResponse();

        servlet.service(request, servletResponse);

        assertThat(response.contentLength()).isEqualTo(5);
        assertThat(response.containsHeaderName()).isEqualTo("Last-Modified");
        assertThat(response.outputStreamRequested()).isFalse();
    }

    private static HttpServletRequest requestWithMethod(final String methodName) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                NoBodyOutputStreamTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new RequestInvocationHandler(methodName));
    }

    private static final class OutputStreamHeadServlet extends HttpServlet {
        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
            response.getOutputStream().write('h');
            response.getOutputStream().write("ello".getBytes(StandardCharsets.UTF_8), 0, 4);
        }
    }

    private static final class RecordedHeadResponse {
        private int contentLength = -1;
        private String containsHeaderName;
        private boolean outputStreamRequested;

        HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    NoBodyOutputStreamTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
        }

        int contentLength() {
            return contentLength;
        }

        String containsHeaderName() {
            return containsHeaderName;
        }

        boolean outputStreamRequested() {
            return outputStreamRequested;
        }

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments)
                throws IOException {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("containsHeader") && arguments.length == 1) {
                containsHeaderName = (String) arguments[0];
                return false;
            }
            if (method.getName().equals("setContentLength") && arguments.length == 1) {
                contentLength = (Integer) arguments[0];
                return null;
            }
            if (method.getName().equals("getOutputStream")) {
                outputStreamRequested = true;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static final class RequestInvocationHandler implements InvocationHandler {
        private final String methodName;

        RequestInvocationHandler(final String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("getMethod")) {
                return methodName;
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
