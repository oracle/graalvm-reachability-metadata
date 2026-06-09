/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Method;

import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7_closure50Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_removeMergedHeadersIfNeeded_closure7_closure50";

    @Test
    void generatedGroovyClassHelperResolvesHeaderClass() throws Exception {
        assertThat(invokeGeneratedClassLookup(Header.class.getName())).isEqualTo(Header.class);
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Exception {
        Class<?> closureClass = RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7_closure50Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
