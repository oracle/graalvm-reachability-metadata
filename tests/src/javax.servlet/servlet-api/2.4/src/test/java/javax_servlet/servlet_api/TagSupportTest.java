/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;

import org.junit.jupiter.api.Test;

class ServletWrapperTest {
    @Test
    void requestWrapperRejectsNullAndTracksWrappedRequests() {
        final ServletRequest firstRequest = RecordedServletRequest.create("first").asServletRequest();
        final ServletRequest secondRequest = RecordedServletRequest.create("second").asServletRequest();
        final ServletRequestWrapper wrapper = new ServletRequestWrapper(firstRequest);

        assertThatThrownBy(() -> new ServletRequestWrapper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request cannot be null");
        assertThat(wrapper.getRequest()).isSameAs(firstRequest);

        wrapper.setRequest(secondRequest);

        assertThat(wrapper.getRequest()).isSameAs(secondRequest);
    }

    @Test
    void requestWrapperDelegatesAccessorsAndMutatorsToWrappedRequest() throws Exception {
        final RecordedServletRequest recordedRequest = RecordedServletRequest.create("request");
        final ServletRequestWrapper wrapper = new ServletRequestWrapper(recordedRequest.asServletRequest());

        wrapper.setCharacterEncoding("UTF-16");

        assertThat(wrapper.getCharacterEncoding()).isEqualTo("UTF-16");
        assertThat(wrapper.getAttribute("user")).isEqualTo("duke");
        assertThat(attributeNames(wrapper)).containsExactly("user");
        assertThat(wrapper.getParameter("mode")).isEqualTo("native");
        assertThat(wrapper.getRemotePort()).isEqualTo(8443);
        assertThat(wrapper.getLocalName()).isEqualTo("localhost");
        assertThat(wrapper.getLocalAddr()).isEqualTo("127.0.0.1");
        assertThat(wrapper.getLocalPort()).isEqualTo(8080);
        assertThat(recordedRequest.characterEncoding()).isEqualTo("UTF-16");
    }

    @Test
    void responseWrapperRejectsNullAndTracksWrappedResponses() {
        final ServletResponse firstResponse = new RecordedServletResponse().asServletResponse();
        final ServletResponse secondResponse = new RecordedServletResponse().asServletResponse();
        final ServletResponseWrapper wrapper = new ServletResponseWrapper(firstResponse);

        assertThatThrownBy(() -> new ServletResponseWrapper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Response cannot be null");
        assertThat(wrapper.getResponse()).isSameAs(firstResponse);

        wrapper.setResponse(secondResponse);

        assertThat(wrapper.getResponse()).isSameAs(secondResponse);
    }

    @Test
    void responseWrapperDelegatesAccessorsAndMutatorsToWrappedResponse() throws IOException {
        final RecordedServletResponse recordedResponse = new RecordedServletResponse();
        final ServletResponseWrapper wrapper = new ServletResponseWrapper(recordedResponse.asServletResponse());

        wrapper.setCharacterEncoding("UTF-16");
        wrapper.setContentLength(42);
        wrapper.setContentType("text/plain");
        wrapper.setBufferSize(8192);
        wrapper.setLocale(Locale.CANADA_FRENCH);
        wrapper.flushBuffer();
        wrapper.resetBuffer();

        assertThat(wrapper.getCharacterEncoding()).isEqualTo("UTF-16");
        assertThat(wrapper.getContentType()).isEqualTo("text/plain");
        assertThat(wrapper.getBufferSize()).isEqualTo(8192);
        assertThat(wrapper.getLocale()).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(wrapper.isCommitted()).isTrue();
        assertThat(recordedResponse.contentLength()).isEqualTo(42);
        assertThat(recordedResponse.contentType()).isEqualTo("text/plain");
        assertThat(recordedResponse.bufferReset()).isTrue();
    }

    private static final class RecordedServletRequest {
        private final String proxyName;
        private String characterEncoding = "UTF-8";

        private RecordedServletRequest(final String proxyName) {
            this.proxyName = proxyName;
        }

        static RecordedServletRequest create(final String proxyName) {
            return new RecordedServletRequest(proxyName);
        }

        ServletRequest asServletRequest() {
            return (ServletRequest) Proxy.newProxyInstance(
                    ServletWrapperTest.class.getClassLoader(),
                    new Class<?>[]{ServletRequest.class},
                    this::handleInvocation);
        }

        String characterEncoding() {
            return characterEncoding;
        }

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments, proxyName);
            }
            return switch (method.getName()) {
                case "getCharacterEncoding" -> characterEncoding;
                case "setCharacterEncoding" -> {
                    characterEncoding = (String) arguments[0];
                    yield null;
                }
                case "getAttribute" -> "user".equals(arguments[0]) ? "duke" : null;
                case "getAttributeNames" -> namedEnumeration("user");
                case "getParameter" -> "mode".equals(arguments[0]) ? "native" : null;
                case "getRemotePort" -> 8443;
                case "getLocalName" -> "localhost";
                case "getLocalAddr" -> "127.0.0.1";
                case "getLocalPort" -> 8080;
                default -> throw new UnsupportedOperationException(method.toGenericString());
            };
        }
    }

    private static final class RecordedServletResponse {
        private int contentLength = -1;
        private int bufferSize;
        private String characterEncoding = "ISO-8859-1";
        private String contentType;
        private Locale locale = Locale.getDefault();
        private boolean committed;
        private boolean bufferReset;

        ServletResponse asServletResponse() {
            return (ServletResponse) Proxy.newProxyInstance(
                    ServletWrapperTest.class.getClassLoader(),
                    new Class<?>[]{ServletResponse.class},
                    this::handleInvocation);
        }

        int contentLength() {
            return contentLength;
        }

        String contentType() {
            return contentType;
        }

        boolean bufferReset() {
            return bufferReset;
        }

        private Object handleInvocation(final Object proxy, final Method method, final Object[] arguments) {
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, arguments, "response");
            }
            return switch (method.getName()) {
                case "setCharacterEncoding" -> {
                    characterEncoding = (String) arguments[0];
                    yield null;
                }
                case "getCharacterEncoding" -> characterEncoding;
                case "setContentLength" -> {
                    contentLength = (Integer) arguments[0];
                    yield null;
                }
                case "setContentType" -> {
                    contentType = (String) arguments[0];
                    yield null;
                }
                case "getContentType" -> contentType;
                case "setBufferSize" -> {
                    bufferSize = (Integer) arguments[0];
                    yield null;
                }
                case "getBufferSize" -> bufferSize;
                case "flushBuffer" -> {
                    committed = true;
                    yield null;
                }
                case "resetBuffer" -> {
                    bufferReset = true;
                    yield null;
                }
                case "isCommitted" -> committed;
                case "setLocale" -> {
                    locale = (Locale) arguments[0];
                    yield null;
                }
                case "getLocale" -> locale;
                default -> throw new UnsupportedOperationException(method.toGenericString());
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> attributeNames(final ServletRequestWrapper wrapper) {
        return Collections.list((Enumeration<String>) wrapper.getAttributeNames());
    }

    private static Enumeration<String> namedEnumeration(final String name) {
        final List<String> names = List.of(name);

        return Collections.enumeration(names);
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass().equals(Object.class);
    }

    private static Object handleObjectMethod(
            final Object proxy,
            final Method method,
            final Object[] arguments,
            final String proxyName) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxyName;
            default -> throw new UnsupportedOperationException(method.toGenericString());
        };
    }
}
