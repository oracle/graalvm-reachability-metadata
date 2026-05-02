/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ConnectionPoint;
import io.lettuce.core.RedisURI;
import io.lettuce.core.dynamic.support.ReflectionUtils;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void findsInterfaceAndClassMethodsAndInvokesClassMethod() {
        Method interfaceMethod = ReflectionUtils.findMethod(ConnectionPoint.class, "getHost");
        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(ConnectionPoint.class);

        Method classMethod = ReflectionUtils.findMethod(RedisURI.class, "getHost");
        assertThat(classMethod).isNotNull();

        RedisURI redisUri = RedisURI.Builder.redis("localhost", 6379).build();
        assertThat(ReflectionUtils.invokeMethod(classMethod, redisUri)).isEqualTo(redisUri.getHost());
    }

    @Test
    void visitsDeclaredFieldsAndReadsPublicStaticField() {
        AtomicReference<Object> value = new AtomicReference<>();

        ReflectionUtils.doWithFields(RedisURI.class,
                field -> value.set(ReflectionUtils.getField(field, null)),
                field -> field.getName().equals("URI_SCHEME_REDIS"));

        assertThat(value.get()).isEqualTo(RedisURI.URI_SCHEME_REDIS);
    }
}
