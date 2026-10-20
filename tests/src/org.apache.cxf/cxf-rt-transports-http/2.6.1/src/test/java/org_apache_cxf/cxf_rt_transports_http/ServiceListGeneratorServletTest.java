/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_transports_http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.DestinationRegistryImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.junit.jupiter.api.Test;

public class ServiceListGeneratorServletTest {
    @Test
    void servesTheBundledServiceListStyleSheetThroughTheServletController() throws Exception {
        ServletContext context = proxy(ServletContext.class, new DefaultHandler());
        Map<String, String> initParameters = new HashMap<String, String>();
        initParameters.put("service-list-path", "/catalog");
        ServletConfig config =
                proxy(ServletConfig.class, new ConfigHandler(context, initParameters));
        CXFNonSpringServlet servlet = new CXFNonSpringServlet(new DestinationRegistryImpl(), false);
        servlet.init(config);

        RequestHandler styleSheetRequestHandler = new RequestHandler(true);
        HttpServletRequest styleSheetRequest =
                proxy(HttpServletRequest.class, styleSheetRequestHandler);
        ResponseHandler styleSheetResponseHandler = new ResponseHandler();
        HttpServletResponse styleSheetResponse =
                proxy(HttpServletResponse.class, styleSheetResponseHandler);

        servlet.service(styleSheetRequest, styleSheetResponse);

        assertThat(styleSheetRequestHandler.attributes).containsKey(Message.BASE_PATH);
        assertThat(styleSheetResponseHandler.contentType).isEqualTo("text/css; charset=UTF-8");

        RequestHandler serviceListRequestHandler = new RequestHandler(false);
        HttpServletRequest serviceListRequest =
                proxy(HttpServletRequest.class, serviceListRequestHandler);
        ResponseHandler serviceListResponseHandler = new ResponseHandler();
        HttpServletResponse serviceListResponse =
                proxy(HttpServletResponse.class, serviceListResponseHandler);

        servlet.service(serviceListRequest, serviceListResponse);
        serviceListResponseHandler.writer.flush();

        assertThat(serviceListResponseHandler.contentType).isEqualTo("text/html; charset=UTF-8");
        assertThat(serviceListResponseHandler.body.toString(StandardCharsets.UTF_8))
                .contains("CXF - Service list", "No services have been found.");
        servlet.destroy();
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static class DefaultHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return defaultValue(method.getReturnType());
        }
    }

    private static final class ConfigHandler extends DefaultHandler {
        private final ServletContext context;
        private final Map<String, String> initParameters;

        private ConfigHandler(ServletContext context, Map<String, String> initParameters) {
            this.context = context;
            this.initParameters = initParameters;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("getServletContext".equals(method.getName())) {
                return context;
            }
            if ("getServletName".equals(method.getName())) {
                return "cxf";
            }
            if ("getInitParameter".equals(method.getName())) {
                return initParameters.get(arguments[0]);
            }
            return super.invoke(proxy, method, arguments);
        }
    }

    private static final class RequestHandler extends DefaultHandler {
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final boolean styleSheetRequested;

        private RequestHandler(boolean styleSheetRequested) {
            this.styleSheetRequested = styleSheetRequested;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if ("getMethod".equals(name)) {
                return "GET";
            }
            if ("getPathInfo".equals(name)) {
                return "/catalog";
            }
            if ("getRequestURI".equals(name)) {
                return "/app/cxf/catalog";
            }
            if ("getRequestURL".equals(name)) {
                return new StringBuffer("http://localhost/app/cxf/catalog");
            }
            if ("getContextPath".equals(name)) {
                return "/app";
            }
            if ("getServletPath".equals(name)) {
                return "/cxf";
            }
            if ("getParameter".equals(name)
                    && "stylesheet".equals(arguments[0])
                    && styleSheetRequested) {
                return "1";
            }
            if ("setAttribute".equals(name)) {
                attributes.put((String) arguments[0], arguments[1]);
                return null;
            }
            if ("getAttribute".equals(name)) {
                return attributes.get(arguments[0]);
            }
            return super.invoke(proxy, method, arguments);
        }
    }

    private static final class ResponseHandler extends DefaultHandler {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final PrintWriter writer = new PrintWriter(body, true);
        private final ServletOutputStream output = new CapturingServletOutputStream(body);
        private String contentType;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("getWriter".equals(method.getName())) {
                return writer;
            }
            if ("getOutputStream".equals(method.getName())) {
                return output;
            }
            if ("setContentType".equals(method.getName())) {
                contentType = (String) arguments[0];
                return null;
            }
            return super.invoke(proxy, method, arguments);
        }
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream output;

        private CapturingServletOutputStream(ByteArrayOutputStream output) {
            this.output = output;
        }

        @Override
        public void write(int value) throws IOException {
            output.write(value);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {}
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
