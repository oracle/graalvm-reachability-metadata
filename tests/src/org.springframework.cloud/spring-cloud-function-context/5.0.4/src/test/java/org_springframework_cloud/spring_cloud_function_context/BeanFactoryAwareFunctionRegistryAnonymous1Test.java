/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanFactoryAwareFunctionRegistryAnonymous1Test {
    @Test
    void pojoFunctionInvocationUsesRegistryCreatedProxyInterceptor() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean("greetingPojo", GreetingPojo.class);
        applicationContext.refresh();
        try {
            BeanFactoryAwareFunctionRegistry registry = new BeanFactoryAwareFunctionRegistry(
                    new DefaultConversionService(),
                    new CompositeMessageConverter(List.of(new StringMessageConverter())),
                    new PassthroughJsonMapper(), null, null);
            registry.setApplicationContext(applicationContext);

            Function<Object, Object> function = registry.lookup(Function.class, "greetingPojo");
            Object result = function.apply("spring");

            assertThat(result).isEqualTo("Hello spring");
        } finally {
            applicationContext.close();
        }
    }

    public static class GreetingPojo {
        public String apply(String name) {
            return "Hello " + name;
        }
    }

    private static final class PassthroughJsonMapper extends JsonMapper {
        @SuppressWarnings("unchecked")
        @Override
        protected <T> T doFromJson(Object json, Type type) {
            return (T) json;
        }

        @Override
        public byte[] toJson(Object value) {
            byte[] json = super.toJson(value);
            return json != null ? json : toString(value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String toString(Object value) {
            return String.valueOf(value);
        }
    }
}
