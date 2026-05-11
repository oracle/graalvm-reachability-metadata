/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.xml.XmlSlurper;
import io.restassured.internal.ContentParser;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ContentParserInner_configureXmlSlurper_closure2Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.ContentParser$_configureXmlSlurper_closure2";

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ContentParser.class,
                MethodHandles.lookup());
        Class<?> closureClass = lookup.findClass(CLOSURE_CLASS_NAME);
        MethodHandle classResolver = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeWithArguments(XmlSlurper.class.getName());

        assertSame(XmlSlurper.class, resolvedClass);
    }
}
