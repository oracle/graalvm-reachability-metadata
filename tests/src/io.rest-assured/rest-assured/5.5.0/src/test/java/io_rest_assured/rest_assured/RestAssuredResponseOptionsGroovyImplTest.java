/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Method;

import io.restassured.internal.RestAssuredResponseOptionsGroovyImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestAssuredResponseOptionsGroovyImplTest {
    @Test
    void generatedGroovyClassHelperResolvesResponseOptionsClassName() throws Exception {
        Method classHelper = RestAssuredResponseOptionsGroovyImpl.class.getDeclaredMethod(
                "class$", String.class);
        classHelper.setAccessible(true);
        String responseOptionsClassName = String.join(
                ".", "io", "restassured", "internal", "RestAssuredResponseOptionsGroovyImpl");

        Class<?> resolvedClass = (Class<?>) classHelper.invoke(null, responseOptionsClassName);

        assertEquals(responseOptionsClassName, resolvedClass.getName());
    }
}
