/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.annotation.AnnotationProxy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Deprecated(since = "5.8")
public class AnnotationProxyTest {

    @Test
    void fallsBackToAnnotationProxyMethodInvocation() throws Throwable {
        Class<AnnotationProxyTest> annotationProxyTestAnnotationAccess = AnnotationProxyTest.class;
        Deprecated annotation = annotationProxyTestAnnotationAccess.getAnnotation(Deprecated.class);
        assertThat(annotation).isNotNull();
        AnnotationProxy<Deprecated> proxy = new AnnotationProxy<>(annotation);

        Method invokeMethod = AnnotationProxy.class.getMethod("invoke", Object.class, Method.class, Object[].class);
        Method annotationTypeMethod = AnnotationProxy.class.getMethod("annotationType");
        Object annotationType = proxy.invoke(proxy, invokeMethod, new Object[] {proxy, annotationTypeMethod, new Object[0]});

        assertThat(annotationType).isEqualTo(Deprecated.class);
    }
}
