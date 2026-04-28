/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class NoBodyResponseTests {
    @Test
    void doHeadRejectsSwitchingFromWriterToOutputStreamWithLocalizedMessage() {
        ConflictingAccessHeadServlet servlet = new ConflictingAccessHeadServlet();
        RecordedHeadResponse response = new RecordedHeadResponse();

        assertThatIllegalStateException()
                .isThrownBy(() -> servlet.invokeDoHead(emptyRequest(), response.asHttpServletResponse()))
                .withMessage("Illegal to call getOutputStream() after getWriter() has been called");

        assertThat(response.writerRequested()).isFalse();
        assertThat(response.outputStreamRequested()).isFalse();
    }

    private static HttpServletRequest emptyRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                NoBodyResponseTests.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                NoBodyResponseTests::handleObjectInvocationOnly);
    }

    static final class ConflictingAccessHeadServlet extends HttpServlet {
        void invokeDoHead(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("hello");
            response.getOutputStream();
        }
    }

    static final class RecordedHeadResponse {
        private boolean writerRequested;
        private boolean outputStreamRequested;

        HttpServletResponse asHttpServletResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    NoBodyResponseTests.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    this::handleInvocation);
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
            if (method.getName().equals("getWriter")) {
                writerRequested = true;
                return new PrintWriter(new StringWriter());
            }
            if (method.getName().equals("getOutputStream")) {
                outputStreamRequested = true;
                return new DiscardingServletOutputStream();
            }
            if (method.getName().equals("setContentLength") || method.getName().equals("setContentLengthLong")) {
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }
    }

    static final class DiscardingServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int value) {
        }

    }

    private static Object handleObjectInvocationOnly(Object proxy, Method method, Object[] arguments) {
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
