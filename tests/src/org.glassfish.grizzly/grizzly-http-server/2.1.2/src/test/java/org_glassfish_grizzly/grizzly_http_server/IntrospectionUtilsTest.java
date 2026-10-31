/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_server;

import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.util.IntrospectionUtils;
import org.glassfish.grizzly.http.server.util.StringParser;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntrospectionUtilsTest {
    @Test
    void bindsStringLongAndBooleanSessionProperties() {
        Session session = new Session("initial");

        assertTrue(IntrospectionUtils.setProperty(session, "idInternal", "customer-session"));
        assertTrue(IntrospectionUtils.setProperty(session, "sessionTimeout", "60000"));
        assertTrue(IntrospectionUtils.setProperty(session, "valid", "false"));

        assertEquals("customer-session", session.getIdInternal());
        assertEquals(60000L, session.getSessionTimeout());
        assertFalse(session.isValid());
        assertEquals("customer-session", IntrospectionUtils.getProperty(session, "idInternal"));
        assertEquals(60000L, IntrospectionUtils.getProperty(session, "sessionTimeout"));
        assertEquals(false, IntrospectionUtils.getProperty(session, "valid"));
    }

    @Test
    void bindsIntegerCacheConfiguration() {
        FileCache cache = new FileCache();

        assertTrue(IntrospectionUtils.setProperty(cache, "maxCacheEntries", "256"));

        assertEquals(256, cache.getMaxCacheEntries());
        assertEquals(256, IntrospectionUtils.getProperty(cache, "maxCacheEntries"));
    }

    @Test
    void readsAndWritesSessionAttributes() throws Exception {
        Session session = new Session("attribute-session");
        List<String> roles = List.of("reader", "editor");

        IntrospectionUtils.setAttribute(session, "roles", roles);

        assertSame(roles, session.getAttribute("roles"));
        assertSame(roles, IntrospectionUtils.getAttribute(session, "roles"));
        assertNull(IntrospectionUtils.getAttribute(session, "missing"));
    }

    @Test
    void executesNamedParserOperation() throws Exception {
        StringParser parser = new StringParser("alpha beta");
        assertEquals(5, parser.findWhite());

        IntrospectionUtils.execute(parser, "reset");

        assertEquals(0, parser.getIndex());
        assertEquals("alpha", parser.extract(0, parser.findWhite()));
    }

    @Test
    void invokesNoArgumentGetter() throws Exception {
        Session session = new Session("dispatch-session");

        assertEquals("dispatch-session", IntrospectionUtils.callMethod0(session, "getIdInternal"));
    }

    @Test
    void invokesSingleArgumentMethodsWithInferredAndNamedTypes() throws Exception {
        StringParser parser = new StringParser("old");

        assertNull(IntrospectionUtils.callMethod1(parser, "setString", "first second",
                "java.lang.String", IntrospectionUtils.class.getClassLoader()));
        assertEquals("first second", parser.getString());
        assertNull(IntrospectionUtils.callMethod1(parser, "setString", "replacement",
                null, null));
        assertEquals("replacement", parser.getString());
    }

    @Test
    void invokesMethodWithMultipleArguments() throws Exception {
        Session session = new Session("multi-argument-session");
        List<String> roles = List.of("reader");

        assertNull(IntrospectionUtils.callMethodN(session, "setAttribute",
                new Object[]{"roles", roles}, new Class<?>[]{String.class, Object.class}));

        assertSame(roles, session.getAttribute("roles"));
    }

    @Test
    void invokesNoArgumentPropertySetter() {
        Response response = Response.create();
        assertFalse(response.isError());

        IntrospectionUtils.setProperty(response, "error");

        assertTrue(response.isError());
        assertEquals(true, IntrospectionUtils.getProperty(response, "error"));
    }

    @Test
    void fallsBackToVoidPropertySetterAndGenericGetter() {
        VoidProperties properties = new VoidProperties();

        assertTrue(IntrospectionUtils.setProperty(properties, "greeting", "hello"));

        assertEquals("hello", properties.getProperty("greeting"));
        assertEquals("hello", IntrospectionUtils.getProperty(properties, "greeting"));
    }

    @Test
    void preservesBooleanPropertySetterResult() {
        BooleanProperties properties = new BooleanProperties();

        assertTrue(IntrospectionUtils.setProperty(properties, "mode", "compact"));
        assertEquals("compact", IntrospectionUtils.getProperty(properties, "mode"));
        assertFalse(IntrospectionUtils.setProperty(properties, "unknown", "ignored"));
        assertEquals("compact", properties.getProperty("mode"));
    }

    @Test
    void fallsBackToStringPropertySetterWhenBooleanOverloadHasAnotherValueType() {
        OverloadedProperties properties = new OverloadedProperties();

        assertTrue(IntrospectionUtils.setProperty(properties, "greeting", "hello"));

        assertEquals("hello", properties.getProperty("greeting"));
        assertEquals("hello", IntrospectionUtils.getProperty(properties, "greeting"));
    }

    @Test
    void createsEmptyUrlLoaderWithParentDelegation() throws Exception {
        ClassLoader parent = IntrospectionUtilsTest.class.getClassLoader();
        URL[] urls = new URL[0];

        try (URLClassLoader loader = assertInstanceOf(URLClassLoader.class,
                IntrospectionUtils.getURLClassLoader(urls, parent))) {
            assertArrayEquals(urls, loader.getURLs());
            assertSame(parent, loader.getParent());
            assertSame(String.class, loader.loadClass("java.lang.String"));
        }
    }

    @Test
    void dispatchesApplicationMainWithArguments() throws Exception {
        CommandLineApplication.arguments = List.of();

        IntrospectionUtils.callMain(CommandLineApplication.class,
                new String[]{"--host", "localhost", "--port", "8080"});

        assertEquals(List.of("--host", "localhost", "--port", "8080"),
                CommandLineApplication.arguments);
    }

    public static class VoidProperties {
        private final Map<String, String> values = new HashMap<>();

        public void setProperty(String name, String value) {
            values.put(name, value);
        }

        public String getProperty(String name) {
            return values.get(name);
        }
    }

    public static class OverloadedProperties extends VoidProperties {
        public boolean setProperty(String name, Integer value) {
            setProperty(name, value.toString());
            return true;
        }
    }

    public static class BooleanProperties {
        private String mode;

        public boolean setProperty(String name, String value) {
            if (!"mode".equals(name)) {
                return false;
            }
            mode = value;
            return true;
        }

        public String getProperty(String name) {
            return "mode".equals(name) ? mode : null;
        }
    }

    public static class CommandLineApplication {
        private static List<String> arguments = List.of();

        public static void main(String[] args) {
            arguments = List.of(args);
        }
    }
}
