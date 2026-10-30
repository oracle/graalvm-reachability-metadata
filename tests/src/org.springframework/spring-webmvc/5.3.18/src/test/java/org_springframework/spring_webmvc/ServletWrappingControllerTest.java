/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ServletWrappingControllerTest {
    @Test
    void managesServletLifecycleAndDelegatesRequests() throws Exception {
        MockServletContext context = new MockServletContext();
        Properties parameters = new Properties();
        parameters.setProperty("greeting", "Welcome");
        ServletWrappingController controller = new ServletWrappingController();
        controller.setServletContext(context);
        controller.setBeanName("greetingServlet");
        controller.setServletClass(GreetingServlet.class);
        controller.setInitParameters(parameters);

        try {
            controller.afterPropertiesSet();
            assertEquals("initialized", context.getAttribute("lifecycle"));

            MockHttpServletRequest firstRequest = new MockHttpServletRequest(context, "GET", "/greet");
            firstRequest.setParameter("name", "Ada");
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            assertNull(controller.handleRequest(firstRequest, firstResponse));
            assertEquals(200, firstResponse.getStatus());
            assertEquals("greetingServlet", firstResponse.getHeader("X-Servlet-Name"));
            assertEquals("Welcome Ada #1", firstResponse.getContentAsString());

            MockHttpServletRequest secondRequest = new MockHttpServletRequest(context, "GET", "/greet");
            secondRequest.setParameter("name", "Grace");
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            assertNull(controller.handleRequest(secondRequest, secondResponse));
            assertEquals("Welcome Grace #2", secondResponse.getContentAsString());
        } finally {
            controller.destroy();
        }
        assertEquals("destroyed", context.getAttribute("lifecycle"));
    }

    public static class GreetingServlet extends HttpServlet {
        private int requestCount;

        @Override
        public void init() {
            getServletContext().setAttribute("lifecycle", "initialized");
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setHeader("X-Servlet-Name", getServletName());
            response.getWriter().write(getInitParameter("greeting") + " " + request.getParameter("name")
                    + " #" + ++requestCount);
        }

        @Override
        public void destroy() {
            getServletContext().setAttribute("lifecycle", "destroyed");
        }
    }
}
