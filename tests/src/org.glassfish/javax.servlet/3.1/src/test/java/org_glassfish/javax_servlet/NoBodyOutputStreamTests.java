/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class NoBodyOutputStreamTests {
    @Test
    void doHeadCountsBytesWrittenThroughOutputStream() throws ServletException, IOException {
        OutputStreamHeadServlet servlet = new OutputStreamHeadServlet();
        RecordedHeadResponse response = new RecordedHeadResponse();

        servlet.invokeDoHead(requestWithProtocol("HTTP/1.1"), response.asHttpServletResponse());

        assertThat(response.contentLength()).isEqualTo(5);
        assertThat(response.writerRequested()).isFalse();
        assertThat(response.outputStreamRequested()).isFalse();
    }

    private static HttpServletRequest requestWithProtocol(String protocol) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                NoBodyOutputStreamTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new RequestInvocationHandler(protocol));
    }

    static final class OutputStreamHeadServlet extends HttpServlet {
        void invokeDoHead(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write('h');
            outputStream.write("ello".getBytes(StandardCharsets.UTF_8), 0, 4);
        }
    }

    static final class RecordedHeadResponse {
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

        private Object handleInvocation(Object proxy, Method method, Object[] arguments) throws IOException {
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

    static final class DiscardingServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int value) {
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
