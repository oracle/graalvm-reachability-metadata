/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class UrlHandlerMappingTest {
    @Test
    void detectsControllerBeanNameAndAlias() throws Exception {
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())) {
            Controller controller = (request, response) -> new ModelAndView("status", "state", "ready");
            context.registerBean("/status", Controller.class, () -> controller);
            context.registerAlias("/status", "/health");
            context.registerBean(BeanNameUrlHandlerMapping.class, BeanNameUrlHandlerMapping::new);
            context.refresh();

            BeanNameUrlHandlerMapping mapping = context.getBean(BeanNameUrlHandlerMapping.class);
            for (String path : new String[]{"/status", "/health"}) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
                ServletRequestPathUtils.parseAndCache(request);
                HandlerExecutionChain chain = mapping.getHandler(request);
                assertNotNull(chain);
                assertSame(controller, chain.getHandler());
                ModelAndView result = new SimpleControllerHandlerAdapter().handle(
                        request, new MockHttpServletResponse(), chain.getHandler());
                assertNotNull(result);
                assertEquals("status", result.getViewName());
                assertEquals("ready", result.getModel().get("state"));
            }
        }
    }

    @Test
    void resolvesExplicitPatternToNamedControllerAndRejectsOtherPaths() throws Exception {
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())) {
            Controller controller = (request, response) -> new ModelAndView("assets", "path", request.getRequestURI());
            context.registerBean("assetController", Controller.class, () -> controller);
            context.registerBean("routes", SimpleUrlHandlerMapping.class,
                    () -> new SimpleUrlHandlerMapping(Map.of("/assets/**", "assetController"), 0));
            context.refresh();

            SimpleUrlHandlerMapping mapping = context.getBean(SimpleUrlHandlerMapping.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/logo.svg");
            ServletRequestPathUtils.parseAndCache(request);
            HandlerExecutionChain chain = mapping.getHandler(request);
            assertNotNull(chain);
            assertSame(controller, chain.getHandler());
            ModelAndView result = new SimpleControllerHandlerAdapter().handle(
                    request, new MockHttpServletResponse(), chain.getHandler());
            assertNotNull(result);
            assertEquals("assets", result.getViewName());
            assertEquals("/assets/logo.svg", result.getModel().get("path"));
            MockHttpServletRequest other = new MockHttpServletRequest("GET", "/other");
            ServletRequestPathUtils.parseAndCache(other);
            assertNull(mapping.getHandler(other));
        }
    }
}
