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
import java.util.List;
import java.util.Map;

import io.restassured.internal.path.json.JSONAssertion;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONAssertionTest {
    @Test
    void compilerGeneratedClassNameResolverCanResolveRuntimeClasses() throws Throwable {
        MethodHandle classResolver = MethodHandles.privateLookupIn(JSONAssertion.class, MethodHandles.lookup())
                .findStatic(JSONAssertion.class, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(classNameSelectedAtRuntime());

        assertThat(resolvedClass).isSameAs(JSONAssertion.class);
    }

    @Test
    void evaluatesNestedJsonPathAgainstJavaObjectGraph() {
        JSONAssertion assertion = new JSONAssertion();
        assertion.setKey("store.book[0].title");
        Map<String, Object> document = Map.of(
                "store",
                Map.of(
                        "book",
                        List.of(
                                Map.of("title", "Sayings of the Century", "price", 8.95d),
                                Map.of("title", "Sword of Honour", "price", 12.99d))));

        try {
            Object title = assertion.getAsJsonObject(document);

            assertThat(title).isEqualTo("Sayings of the Century");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String classNameSelectedAtRuntime() {
        String[] nameParts = new String[] {JSONAssertion.class.getPackageName(), ".", "JSONAssertion" };
        return nameParts[0] + nameParts[1] + nameParts[2];
    }
}
