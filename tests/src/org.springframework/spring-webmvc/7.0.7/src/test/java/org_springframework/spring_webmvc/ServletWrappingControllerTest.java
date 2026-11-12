/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.IOException;
import java.util.Properties;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ServletWrappingControllerTest {
    @Test
    void managesServletLifecycleAndDelegatesRequestsWithConfiguredParameters() throws Exception {
        MockServletContext servletContext = new MockServletContext();
        ServletWrappingController controller = new ServletWrappingController();
        controller.setServletContext(servletContext);
        controller.setBeanName("greetingController");
        controller.setServletClass(GreetingServlet.class);
        Properties parameters = new Properties();
        parameters.setProperty("greeting", "Welcome");
        controller.setInitParameters(parameters);
        controller.afterPropertiesSet();
        try {
            assertEquals("greetingController", servletContext.getAttribute("initializedServlet"));
            MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/greet");
            request.addParameter("name", "Ada");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertNull(controller.handleRequest(request, response));

            assertEquals(200, response.getStatus());
            assertEquals("text/plain", response.getContentType());
            assertEquals("Welcome Ada from greetingController", response.getContentAsString());
            assertNull(servletContext.getAttribute("destroyedServlet"));
        }
        finally {
            controller.destroy();
        }
        assertEquals("greetingController", servletContext.getAttribute("destroyedServlet"));
    }

    public static class GreetingServlet extends HttpServlet {
        @Override
        public void init() {
            getServletContext().setAttribute("initializedServlet", getServletName());
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().write(getInitParameter("greeting") + " " + request.getParameter("name")
                    + " from " + getServletName());
        }

        @Override
        public void destroy() {
            getServletContext().setAttribute("destroyedServlet", getServletName());
        }
    }
}
