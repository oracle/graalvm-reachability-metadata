/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.xml.XmlSlurper;
import io.restassured.config.XmlConfig;
import io.restassured.internal.ContentParser;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ContentParserInner_configureXmlSlurper_closure1Test {
    @Test
    void compilerGeneratedClosureClassResolverResolvesXmlSlurper() throws Throwable {
        RecordingXmlSlurper xmlSlurper = new RecordingXmlSlurper();
        XmlConfig xmlConfig = new XmlConfig().property("urn:test:property", "configured");

        configureXmlSlurper().invoke(xmlSlurper, xmlConfig);

        Class<?> closureClass = xmlSlurper.getConfigurationClosureClass();
        assertNotNull(closureClass);
        Object resolvedClass = invokeCompilerGeneratedClassResolver(closureClass, XmlSlurper.class.getName());

        assertSame(XmlSlurper.class, resolvedClass);
    }

    private static MethodHandle configureXmlSlurper() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ContentParser.class, MethodHandles.lookup());
        return lookup.findStatic(
                ContentParser.class,
                "configureXmlSlurper",
                MethodType.methodType(XmlSlurper.class, XmlSlurper.class, XmlConfig.class));
    }

    private static Object invokeCompilerGeneratedClassResolver(
            Class<?> closureClass,
            String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return classResolver.invokeWithArguments(className);
    }

    private static final class RecordingXmlSlurper extends XmlSlurper {
        private static final String CONFIGURATION_CLOSURE_CLASS_NAME =
                "io.restassured.internal.ContentParser$_configureXmlSlurper_closure1";

        private final AtomicReference<Class<?>> configurationClosureClass = new AtomicReference<>();

        private RecordingXmlSlurper() throws Exception {
            super(false, true, false);
        }

        @Override
        public void setProperty(String name, Object value)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            Class<?> callerClass = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
                    .walk(frames -> frames
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(type -> CONFIGURATION_CLOSURE_CLASS_NAME.equals(type.getName()))
                            .findFirst()
                            .orElse(null));
            if (callerClass != null) {
                configurationClosureClass.set(callerClass);
            }
        }

        private Class<?> getConfigurationClosureClass() {
            return configurationClosureClass.get();
        }
    }
}
