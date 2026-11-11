/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.server.HessianServlet;
import com.caucho.services.server.Service;
import java.util.Map;
import javax.servlet.ServletConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class HessianServletTest {
    @Test
    void loadsConfiguredServiceAndInitializesIt() throws Exception {
        ServiceImpl.last = null;
        HessianServlet servlet = new HessianServlet();
        servlet.init(new ServletSupport(Map.of("service-class", ServiceImpl.class.getName(),
                "api-class", RpcSupport.Echo.class.getName(), "prefix", "configured:")));
        assertEquals("configured:snapshot", ServiceImpl.last.echo("snapshot"));
        assertEquals(RpcSupport.Echo.class, servlet.getAPIClass());
        servlet.destroy();
    }

    @Test
    void loadsHomeAndObjectServicesWithoutContextClassLoader() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        HessianServlet servlet = new HessianServlet();
        int before = ServiceImpl.constructions;
        try {
            Thread.currentThread().setContextClassLoader(null);
            servlet.init(new ServletSupport(Map.of("home-class", ServiceImpl.class.getName(),
                    "home-api", RpcSupport.Echo.class.getName(),
                    "object-class", ServiceImpl.class.getName(),
                    "object-api", RpcSupport.Echo.class.getName(), "prefix", "object:")));
            assertEquals(before + 2, ServiceImpl.constructions);
            assertEquals("object:snapshot", ServiceImpl.last.echo("snapshot"));
            assertEquals(RpcSupport.Echo.class, servlet.getAPIClass());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            servlet.destroy();
        }
    }

    public static class ServiceImpl implements RpcSupport.Echo, Service {
        private static ServiceImpl last;
        private static int constructions;
        private String prefix;

        public ServiceImpl() {
            last = this;
            constructions++;
        }

        @Override
        public void init(ServletConfig config) {
            prefix = config.getInitParameter("prefix");
        }

        @Override
        public String echo(String text) {
            return prefix + text;
        }

        @Override
        public void destroy() {
            prefix = null;
        }
    }
}
