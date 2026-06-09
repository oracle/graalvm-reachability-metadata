/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.MapCreator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapCreatorInnerArgsAndValueTest {
    @Test
    void groovyObjectDispatchResolvesGeneratedClassHelper() {
        MapCreator.ArgsAndValue argsAndValue = new MapCreator.ArgsAndValue();
        String className = "io.restassured.builder.MultiPartSpecBuilder";

        Class<?> resolvedClass = (Class<?>) argsAndValue.invokeMethod(
                "class$",
                new Object[] {className});

        assertEquals(className, resolvedClass.getName());
    }
}
