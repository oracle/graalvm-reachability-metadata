/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.server.BurlapServlet;
import com.caucho.services.server.Service;
import java.util.Map;
import javax.servlet.ServletConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BurlapServletTest {
    @Test
    void loadsConfiguredServiceAndInitializesIt() throws Exception {
        ServiceImpl.last = null;
        BurlapServlet servlet = new BurlapServlet();
        servlet.init(new ServletSupport(Map.of("service-class", ServiceImpl.class.getName(),
                "api-class", RpcSupport.Echo.class.getName(), "prefix", "configured:")));
        assertEquals("configured:snapshot", ServiceImpl.last.echo("snapshot"));
        servlet.destroy();
    }

    public static class ServiceImpl implements RpcSupport.Echo, Service {
        private static ServiceImpl last;
        private String prefix;

        public ServiceImpl() {
            last = this;
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
