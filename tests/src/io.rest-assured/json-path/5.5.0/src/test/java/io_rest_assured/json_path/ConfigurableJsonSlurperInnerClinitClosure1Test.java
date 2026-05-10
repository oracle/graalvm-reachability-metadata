/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.json_path;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.util.Map;

import io.restassured.internal.path.json.ConfigurableJsonSlurper;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurableJsonSlurperInnerClinitClosure1Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.path.json.ConfigurableJsonSlurper$__clinit__closure1";

    @Test
    void staticGetValueClosureConvertsConfiguredJsonNumbersToFloatingPointTypes() {
        ConfigurableJsonSlurper floatSlurper = new ConfigurableJsonSlurper(NumberReturnType.FLOAT_AND_DOUBLE);
        Map<?, ?> floatDocument = (Map<?, ?>) floatSlurper.parseText("""
                {"small": 12.5}
                """);

        ConfigurableJsonSlurper doubleSlurper = new ConfigurableJsonSlurper(NumberReturnType.DOUBLE);
        Map<?, ?> doubleDocument = (Map<?, ?>) doubleSlurper.parseText("""
                {"small": 12.5}
                """);

        assertThat(floatDocument.get("small")).isEqualTo(12.5f);
        assertThat(doubleDocument.get("small")).isEqualTo(12.5d);
    }

    @Test
    void compilerGeneratedClassNameResolverCanResolveRuntimeClasses() throws Throwable {
        MethodHandle classResolver = closureClassNameResolver();

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(classNameSelectedAtRuntime());

        assertThat(resolvedClass).isSameAs(BigDecimal.class);
    }

    @Test
    void compilerGeneratedClassNameResolverReportsMissingRuntimeClasses() throws Throwable {
        MethodHandle classResolver = closureClassNameResolver();
        String missingClassName = missingClassNameSelectedAtRuntime();

        assertThatThrownBy(() -> {
            Class<?> ignored = (Class<?>) classResolver.invokeExact(missingClassName);
            assertThat(ignored).isNotNull();
        })
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessage(missingClassName);
    }

    private static MethodHandle closureClassNameResolver() throws ReflectiveOperationException {
        Class<?> closureClass = Class.forName(CLOSURE_CLASS_NAME);
        return MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup())
                .findStatic(closureClass, "class$", MethodType.methodType(Class.class, String.class));
    }

    private static String classNameSelectedAtRuntime() {
        String[] nameParts = new String[] {"java.math.", "BigDecimal" };
        return nameParts[0] + nameParts[1];
    }

    private static String missingClassNameSelectedAtRuntime() {
        String[] nameParts = new String[] {"io.restassured.internal.path.json.", "MissingJsonValue" };
        return nameParts[0] + nameParts[1];
    }
}
