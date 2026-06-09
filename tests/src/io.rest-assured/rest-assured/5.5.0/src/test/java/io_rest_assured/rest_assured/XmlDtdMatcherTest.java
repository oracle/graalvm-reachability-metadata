/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.restassured.internal.matcher.xml.XmlDtdMatcher;
import org.junit.jupiter.api.Test;

import static io.restassured.matcher.RestAssuredMatchers.matchesDtd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

public class XmlDtdMatcherTest {
    @Test
    void validatesXmlDocumentAgainstDtdMatcher() {
        String dtd = """
                <!ELEMENT greeting (message)>
                <!ELEMENT message (#PCDATA)>
                """;
        String xml = """
                <greeting>
                    <message>Hello from Rest Assured</message>
                </greeting>
                """;

        assertThat(xml, matchesDtd(dtd));
    }

    @Test
    void resolvesClassThroughGeneratedGroovyClassLookup() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(XmlDtdMatcher.class.getName());

        assertSame(XmlDtdMatcher.class, resolvedClass);
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(XmlDtdMatcher.class, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                XmlDtdMatcher.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }
}
