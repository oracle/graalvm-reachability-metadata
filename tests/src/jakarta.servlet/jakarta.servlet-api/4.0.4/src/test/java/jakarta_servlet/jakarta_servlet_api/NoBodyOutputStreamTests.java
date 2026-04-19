/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

class NoBodyOutputStreamTests {
    @Test
    void doHeadCountsBytesWrittenThroughWriter() throws ServletException, IOException {
        final WriterHeadServlet servlet = new WriterHeadServlet();
        final RecordedHeadResponse response = new RecordedHeadResponse();

        servlet.invokeDoHead(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.contentLength()).isEqualTo(5);
        assertThat(response.writerRequested()).isFalse();
        assertThat(response.outputStreamRequested()).isFalse();
    }

    @Test
    void doHeadCountsBytesWrittenThroughOutputStream() throws ServletException, IOException {
        final OutputStreamHeadServlet servlet = new OutputStreamHeadServlet();
        final RecordedHeadResponse response = new RecordedHeadResponse();

        servlet.invokeDoHead(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.contentLength()).isEqualTo(5);
        assertThat(response.writerRequested()).isFalse();
        assertThat(response.outputStreamRequested()).isFalse();
    }

    private static HttpServletRequest requestWithProtocol(final String protocol) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                NoBodyOutputStreamTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new RequestInvocationHandler(protocol));
    }

    private static final class WriterHeadServlet extends HttpServlet {
        void invokeDoHead(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws IOException {
            final PrintWriter writer = response.getWriter();
            writer.write("hello");
            writer.flush();
        }
    }

    private static final class OutputStreamHeadServlet extends HttpServlet {
        void invokeDoHead(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws IOException {
            final ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write('h');
            outputStream.write("ello".getBytes(StandardCharsets.UTF_8), 0, 4);
        }
    }

    private static final class RecordedHeadResponse {
        private int contentLength = -1;
        private boolean writerRequested;
        private boolean outputStreamRequested;

        HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    NoBodyOutputStreamTests.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
        }

        int contentLength() {
            return contentLength;
        }

        boolean writerRequested() {
            return writerRequested;
        }

        boolean outputStreamRequested() {
            return outputStreamRequested;
        }

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments)
                throws IOException {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if (method.getName().equals("getCharacterEncoding")) {
                return "UTF-8";
            }
            if (method.getName().equals("setContentLength") && arguments.length == 1) {
                contentLength = (Integer) arguments[0];
                return null;
            }
            if (method.getName().equals("setContentLengthLong") && arguments.length == 1) {
                contentLength = Math.toIntExact((Long) arguments[0]);
                return null;
            }
            if (method.getName().equals("getWriter")) {
                writerRequested = true;
                return new PrintWriter(new StringWriter());
            }
            if (method.getName().equals("getOutputStream")) {
                outputStreamRequested = true;
                return new DiscardingServletOutputStream();
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    private static final class DiscardingServletOutputStream extends ServletOutputStream {
        @Override
        public void write(final int value) {
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(final WriteListener writeListener) {
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
