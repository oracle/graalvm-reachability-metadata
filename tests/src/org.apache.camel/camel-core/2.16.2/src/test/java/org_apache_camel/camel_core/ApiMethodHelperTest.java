/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodHelper;
import org.apache.camel.util.component.ApiMethodImpl;
import org.junit.jupiter.api.Test;

public class ApiMethodHelperTest {
    @Test
    void invokesMethodAfterConvertingCollectionToObjectArrayParameter() {
        ExampleApi proxy = new ExampleApi();
        ApiMethod method = new ApiMethodImpl(ExampleApi.class, String.class, "join", String[].class, "values");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("values", Arrays.asList("alpha", "beta", "gamma"));

        Object result = ApiMethodHelper.invokeMethod(proxy, method, properties);

        assertThat(result).isEqualTo("alpha,beta,gamma");
        assertThat(proxy.getLastObjectArray()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void invokesMethodAfterConvertingDerivedArrayToDeclaredSuperArrayParameter() {
        ExampleApi proxy = new ExampleApi();
        ApiMethod method = new ApiMethodImpl(ExampleApi.class, Number[].class, "numbers", Number[].class, "values");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("values", new Integer[] {1, 2, 3});

        Object result = ApiMethodHelper.invokeMethod(proxy, method, properties);

        assertThat((Number[]) result).containsExactly(1, 2, 3);
        assertThat(proxy.getLastNumberArray()).isExactlyInstanceOf(Number[].class).containsExactly(1, 2, 3);
    }

    public static class ExampleApi {
        private String[] lastObjectArray;
        private Number[] lastNumberArray;

        public String join(String[] values) {
            lastObjectArray = values;
            return values[0] + "," + values[1] + "," + values[2];
        }

        public Number[] numbers(Number[] values) {
            lastNumberArray = values;
            return values;
        }

        public String[] getLastObjectArray() {
            return lastObjectArray;
        }

        public Number[] getLastNumberArray() {
            return lastNumberArray;
        }
    }
}
