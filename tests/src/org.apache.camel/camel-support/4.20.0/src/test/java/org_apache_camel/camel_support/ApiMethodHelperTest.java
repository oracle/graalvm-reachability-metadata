/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodHelper;
import org.apache.camel.support.component.ApiMethodImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiMethodHelperTest {
    @Test
    void invokeMethodConvertsCollectionToArrayParameter() {
        SampleApi api = new SampleApiImpl();
        Map<String, Object> properties = Map.of("values", List.of("alpha", "beta"));

        Object result = ApiMethodHelper.invokeMethod(api, SampleApiMethod.JOIN_OBJECT_ARRAY, properties);

        assertThat(result).isEqualTo("alpha,beta");
    }

    @Test
    void invokeMethodConvertsDerivedArrayToDeclaredSuperArrayParameter() {
        SampleApi api = new SampleApiImpl();
        Map<String, Object> properties = Map.of("values", new Integer[] {1, 2, 3 });

        Object result = ApiMethodHelper.invokeMethod(api, SampleApiMethod.SUM_NUMBERS, properties);

        assertThat(result).isEqualTo(6);
    }

    public interface SampleApi {
        String joinObjectArray(Object[] values);

        int sumNumbers(Number[] values);
    }

    public static final class SampleApiImpl implements SampleApi {
        @Override
        public String joinObjectArray(Object[] values) {
            return String.join(",", Arrays.stream(values).map(Object::toString).toList());
        }

        @Override
        public int sumNumbers(Number[] values) {
            return Arrays.stream(values).mapToInt(Number::intValue).sum();
        }
    }

    public enum SampleApiMethod implements ApiMethod {
        JOIN_OBJECT_ARRAY(String.class, "joinObjectArray", ApiMethodArg.arg("values", Object[].class)),
        SUM_NUMBERS(int.class, "sumNumbers", ApiMethodArg.arg("values", Number[].class));

        private final ApiMethodImpl apiMethod;

        SampleApiMethod(Class<?> resultType, String name, ApiMethodArg... args) {
            this.apiMethod = new ApiMethodImpl(SampleApi.class, resultType, name, args);
        }

        @Override
        public String getName() {
            return apiMethod.getName();
        }

        @Override
        public Class<?> getResultType() {
            return apiMethod.getResultType();
        }

        @Override
        public List<String> getArgNames() {
            return apiMethod.getArgNames();
        }

        @Override
        public List<String> getSetterArgNames() {
            return apiMethod.getSetterArgNames();
        }

        @Override
        public List<Class<?>> getArgTypes() {
            return apiMethod.getArgTypes();
        }

        @Override
        public Method getMethod() {
            return apiMethod.getMethod();
        }
    }
}
