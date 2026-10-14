/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jnr.ffi.util.AnnotationProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value() default "default";
    }

    @Label("configured")
    public static class LabeledType {
    }

    @Test
    void proxyBehavesLikeADeclaredAnnotation() {
        AnnotationProxy<Label> proxy = AnnotationProxy.newProxy(Label.class);
        proxy.setProperty("value", "configured");
        Label annotation = proxy.getProxedAnnotation();

        assertThat(annotation.value()).isEqualTo("configured");
        assertThat(proxy).isEqualTo(LabeledType.class.getAnnotation(Label.class));
        assertThat(annotation.toString()).contains("configured");
    }
}
