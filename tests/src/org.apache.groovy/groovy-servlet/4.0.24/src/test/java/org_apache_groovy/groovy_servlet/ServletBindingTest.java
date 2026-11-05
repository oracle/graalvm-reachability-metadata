/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_servlet;

import groovy.json.StreamingJsonBuilder;
import groovy.servlet.ServletBinding;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ServletBindingTest {
    @Test
    void jsonBindingWritesToServletResponse() throws Exception {
        MockServletContext context = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(context);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletBinding binding = new ServletBinding(request, response, context);

        StreamingJsonBuilder json = assertInstanceOf(StreamingJsonBuilder.class, binding.getVariable("json"));
        assertSame(json, binding.getVariable("json"));
        assertEquals("", response.getContentAsString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Hello \"servlet\"");
        payload.put("items", List.of("alpha", "beta"));
        payload.put("ready", true);
        json.call(payload);
        PrintWriter out = assertInstanceOf(PrintWriter.class, binding.getVariable("out"));
        out.flush();

        assertEquals("""
                {"message":"Hello \\"servlet\\"","items":["alpha","beta"],"ready":true}""",
                response.getContentAsString());
    }
}
