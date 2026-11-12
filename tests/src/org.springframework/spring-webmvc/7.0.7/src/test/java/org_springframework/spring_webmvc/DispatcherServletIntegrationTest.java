/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.RequestToViewNameTranslator;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DispatcherServletIntegrationTest {
    private GenericWebApplicationContext context;
    private DispatcherServlet servlet;

    @BeforeEach
    void startServlet() throws Exception {
        MockServletContext servletContext = new MockServletContext();
        context = new GenericWebApplicationContext(servletContext);
        MvcConfiguration mvc = new MvcConfiguration();
        mvc.setApplicationContext(context);
        mvc.setServletContext(servletContext);
        ContentNegotiationManager negotiation = mvc.mvcContentNegotiationManager();
        FormattingConversionService conversion = mvc.mvcConversionService();
        ResourceUrlProvider resources = mvc.mvcResourceUrlProvider();
        context.registerBean(CatalogController.class, CatalogController::new);
        context.registerBean("mvcConversionService", FormattingConversionService.class, () -> conversion);
        context.registerBean("mvcResourceUrlProvider", ResourceUrlProvider.class, () -> resources);
        context.registerBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class,
                () -> mvc.requestMappingHandlerMapping(negotiation, mvc.mvcApiVersionStrategy(), conversion, resources));
        context.registerBean("requestMappingHandlerAdapter", RequestMappingHandlerAdapter.class,
                () -> mvc.requestMappingHandlerAdapter(negotiation, conversion, mvc.mvcValidator()));
        context.registerBean("httpRequestHandlerAdapter", HttpRequestHandlerAdapter.class, mvc::httpRequestHandlerAdapter);
        context.registerBean("handlerExceptionResolver", HandlerExceptionResolver.class,
                () -> mvc.handlerExceptionResolver(negotiation));
        context.registerBean("mvcViewResolver", ViewResolver.class, () -> mvc.mvcViewResolver(negotiation));
        context.registerBean("localeResolver", LocaleResolver.class, mvc::localeResolver);
        context.registerBean("flashMapManager", FlashMapManager.class, mvc::flashMapManager);
        context.registerBean("viewNameTranslator", RequestToViewNameTranslator.class, mvc::viewNameTranslator);
        context.refresh();
        servlet = new DispatcherServlet(context);
        MockServletConfig config = new MockServletConfig(servletContext, "catalog");
        config.addInitParameter("dispatchOptionsRequest", "true");
        config.addInitParameter("publishContext", "false");
        servlet.init(config);
    }

    @AfterEach
    void stopServlet() {
        if (servlet != null) {
            servlet.destroy();
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void resolvesRequestLocaleAndAppliesCachePolicy() throws Exception {
        MockHttpServletRequest request = request("GET", "/locale");
        request.addPreferredLocale(Locale.FRENCH);
        MockHttpServletResponse response = dispatch(request);
        assertEquals(200, response.getStatus());
        assertEquals("fr", response.getContentAsString());
        assertEquals("max-age=30", response.getHeader("Cache-Control"));
    }

    @Test
    void invokesImplicitOptionsHandler() throws Exception {
        MockHttpServletResponse response = dispatch(request("OPTIONS", "/locale"));
        assertEquals(200, response.getStatus());
        String allowed = response.getHeader("Allow");
        assertNotNull(allowed);
        assertTrue(allowed.contains("GET"));
        assertTrue(allowed.contains("HEAD"));
        assertTrue(allowed.contains("OPTIONS"));
    }

    @Test
    void handlesAmbiguousCorsPreflightWithoutInvokingController() throws Exception {
        MockHttpServletRequest request = request("OPTIONS", "/selection/book");
        request.addHeader("Origin", "https://client.example");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = dispatch(request);
        assertEquals(200, response.getStatus());
        assertEquals("https://client.example", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("GET"));
    }

    @Test
    void bindsAndValidatesModelWithoutBeanValidationProvider() throws Exception {
        MockHttpServletRequest request = request("GET", "/quantity");
        request.addParameter("count", "4");
        MockHttpServletResponse response = dispatch(request);
        assertEquals(200, response.getStatus());
        assertEquals("count=4,errors=0", response.getContentAsString());
    }

    @Test
    void resolvesMissingParameterThroughExceptionResolverChain() throws Exception {
        MockHttpServletResponse response = dispatch(request("GET", "/required"));
        assertEquals(400, response.getStatus());
    }

    @Test
    void translatesRequestPathIntoCachedViewAndRendersModel() throws Exception {
        MockHttpServletRequest request = request("GET", "/reports/summary.html");
        MockHttpServletResponse response = dispatch(request);
        assertEquals(200, response.getStatus());
        assertEquals("/templates/reports/summary.jsp", response.getForwardedUrl());
        assertEquals("inventory", request.getAttribute("title"));

        MockHttpServletResponse repeated = dispatch(request("GET", "/reports/summary.html"));
        assertEquals(response.getForwardedUrl(), repeated.getForwardedUrl());
    }

    @Test
    void redirectsAndConsumesSessionFlashAttributeOnlyOnce() throws Exception {
        MockHttpServletRequest save = request("GET", "/save");
        MockHttpServletResponse redirect = dispatch(save);
        assertEquals(302, redirect.getStatus());
        assertEquals("/saved", redirect.getRedirectedUrl());
        MockHttpSession session = (MockHttpSession) save.getSession(false);
        assertNotNull(session);

        MockHttpServletRequest follow = request("GET", "/saved");
        follow.setSession(session);
        assertEquals("stored", dispatch(follow).getContentAsString());
        MockHttpServletRequest again = request("GET", "/saved");
        again.setSession(session);
        assertEquals("none", dispatch(again).getContentAsString());
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(context.getServletContext(), method, path);
    }

    private MockHttpServletResponse dispatch(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        return response;
    }

    public static class MvcConfiguration extends WebMvcConfigurationSupport {
        @Override
        protected void configureViewResolvers(ViewResolverRegistry registry) {
            UrlBasedViewResolver resolver = new UrlBasedViewResolver();
            resolver.setViewClass(InternalResourceView.class);
            resolver.setPrefix("/templates/");
            resolver.setSuffix(".jsp");
            registry.viewResolver(resolver);
        }

        @Override
        protected RequestMappingHandlerAdapter createRequestMappingHandlerAdapter() {
            RequestMappingHandlerAdapter adapter = super.createRequestMappingHandlerAdapter();
            adapter.setCacheSeconds(30);
            return adapter;
        }
    }

    @Controller
    public static class CatalogController {
        @GetMapping("/locale")
        @ResponseBody
        public String locale(Locale locale) {
            return locale.getLanguage();
        }

        @GetMapping("/quantity")
        @ResponseBody
        public String quantity(@Validated @ModelAttribute("quantity") Quantity quantity, BindingResult errors) {
            return "count=" + quantity.getCount() + ",errors=" + errors.getErrorCount();
        }

        @GetMapping("/required")
        @ResponseBody
        public String required(@RequestParam("value") String value) {
            return value;
        }

        @GetMapping("/reports/summary.html")
        public void summary(Model model) {
            model.addAttribute("title", "inventory");
        }

        @GetMapping("/save")
        public String save(RedirectAttributes attributes) {
            attributes.addFlashAttribute("notice", "stored");
            return "redirect:/saved";
        }

        @GetMapping("/saved")
        @ResponseBody
        public String saved(Model model) {
            return (String) model.asMap().getOrDefault("notice", "none");
        }

        @CrossOrigin
        @GetMapping("/selection/{name}")
        @ResponseBody
        public String books() {
            return "books";
        }

        @CrossOrigin
        @GetMapping("/selection/*")
        @ResponseBody
        public String films() {
            return "films";
        }
    }

    public static class Quantity {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
