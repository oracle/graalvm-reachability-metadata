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
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class NoBodyOutputStreamTest {
    @Test
    void legacyHeadHandlingCountsWrittenBodyWithoutSendingIt() throws ServletException, IOException {
        BodyWritingServlet servlet = new BodyWritingServlet("hello");
        RecordedContentLengthResponse response = new RecordedContentLengthResponse();

        servlet.init(configWithLegacyHeadHandlingEnabled());
        servlet.invokeDoHead(request(), response.asHttpServletResponse());

        assertThat(response.contentLength()).isEqualTo(5);
    }

    private static ServletConfig configWithLegacyHeadHandlingEnabled() {
        return (ServletConfig) Proxy.newProxyInstance(
                NoBodyOutputStreamTest.class.getClassLoader(),
                new Class<?>[]{ServletConfig.class},
                new ServletConfigInvocationHandler());
    }

    private static HttpServletRequest request() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                NoBodyOutputStreamTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                NoBodyOutputStreamTest::handleUnsupportedInvocation);
    }

    private static final class BodyWritingServlet extends HttpServlet {
        private final byte[] body;

        private BodyWritingServlet(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        private void invokeDoHead(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getOutputStream().write(body);
        }
    }

    private static final class ServletConfigInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("getInitParameter")) {
                return HttpServlet.LEGACY_DO_HEAD.equals(arguments[0]) ? "true" : null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static final class RecordedContentLengthResponse {
        private int contentLength = -1;

        private HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    NoBodyOutputStreamTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
        }

        private int contentLength() {
            return contentLength;
        }

        private Object handleInvocation(Object proxy, Method method, Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("setContentLength") && arguments.length == 1) {
                contentLength = (Integer) arguments[0];
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static Object handleUnsupportedInvocation(Object proxy, Method method, Object[] arguments) {
        if (isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, arguments);
        }
        throw new UnsupportedOperationException(method.toGenericString());
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
