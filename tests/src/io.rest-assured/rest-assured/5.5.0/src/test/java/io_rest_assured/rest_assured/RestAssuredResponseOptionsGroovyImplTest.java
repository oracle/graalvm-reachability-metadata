/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RestAssuredResponseOptionsGroovyImplDirectAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestAssuredResponseOptionsGroovyImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolverForReachableJdkType() {
        Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplDirectAccess
                .resolveWithCompilerGeneratedClassResolver("java.lang.String");

        assertSame(String.class, resolvedClass);
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() {
        final String missingClassName = "io.restassured.internal.RestAssuredResponseOptionsGroovyImplMissingClass";
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> RestAssuredResponseOptionsGroovyImplDirectAccess
                        .resolveWithCompilerGeneratedClassResolver(missingClassName));

        assertEquals(missingClassName, error.getMessage());
    }
}
