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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.restassured.internal.path.json.ConfigurableJsonSlurper;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurableJsonSlurperTest {
    @Test
    void parsesNestedJsonUsingConfiguredBigIntegerNumbers() {
        ConfigurableJsonSlurper slurper = new ConfigurableJsonSlurper(NumberReturnType.BIG_INTEGER);

        Object parsed = slurper.parseText("""
                {
                  "id": 123,
                  "price": 12.50,
                  "tags": ["rest", "json"],
                  "active": true,
                  "nested": {"count": 7}
                }
                """);

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> document = (Map<?, ?>) parsed;
        assertThat(document.get("id")).isEqualTo(new BigInteger("123"));
        assertThat(document.get("price")).isEqualTo(new BigDecimal("12.50"));
        assertThat(document.get("active")).isEqualTo(Boolean.TRUE);
        assertThat(document.get("tags"))
                .isInstanceOf(List.class)
                .asList()
                .containsExactly("rest", "json");
        assertThat(document.get("nested"))
                .isInstanceOf(Map.class)
                .extracting(nested -> ((Map<?, ?>) nested).get("count"))
                .isEqualTo(new BigInteger("7"));
    }

    @Test
    void generatedClassNameResolverCanResolveRuntimeClasses() throws Throwable {
        MethodHandle classResolver = MethodHandles.privateLookupIn(ConfigurableJsonSlurper.class, MethodHandles.lookup())
                .findStatic(ConfigurableJsonSlurper.class, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(String.class.getName());

        assertThat(resolvedClass).isSameAs(String.class);
    }
}
