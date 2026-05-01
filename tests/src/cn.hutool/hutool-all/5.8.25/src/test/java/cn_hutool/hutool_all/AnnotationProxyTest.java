/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.annotation.Alias;
import cn.hutool.core.annotation.AnnotationProxy;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyTest {
    @Test
    public void proxiesAnnotationAttributesAndObjectMethods() throws Throwable {
        AliasedMarker sourceAnnotation = AnnotatedSubject.class.getAnnotation(AliasedMarker.class);
        assertThat(sourceAnnotation).isNotNull();
        AnnotationProxy<AliasedMarker> annotationProxy = new AnnotationProxy<>(sourceAnnotation);

        Object rawProxy = Proxy.newProxyInstance(
                AnnotationProxyTest.class.getClassLoader(),
                new Class<?>[] {AliasedMarker.class, InvocationHandler.class},
                annotationProxy);
        AliasedMarker proxy = (AliasedMarker) rawProxy;
        InvocationHandler invocationHandler = (InvocationHandler) rawProxy;

        assertThat(proxy.value()).isEqualTo("primary");
        assertThat(proxy.name()).isEqualTo("primary");
        assertThat(proxy.priority()).isEqualTo(7);
        assertThat(proxy.annotationType()).isEqualTo(AliasedMarker.class);
        assertThat(proxy.toString()).contains("primary");
        assertThat(invocationHandler.invoke(rawProxy, AliasedMarker.class.getMethod("value"), new Object[0]))
                .isEqualTo("primary");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AliasedMarker {
        String value();

        @Alias("value")
        String name() default "";

        int priority() default 1;
    }

    @AliasedMarker(value = "primary", priority = 7)
    public static class AnnotatedSubject {
    }
}
