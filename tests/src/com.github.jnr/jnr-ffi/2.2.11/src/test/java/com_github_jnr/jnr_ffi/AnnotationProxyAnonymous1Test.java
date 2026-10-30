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

public class AnnotationProxyAnonymous1Test {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Options {
        int count() default 3;

        String name() default "ffi";
    }

    @Test
    void discoversAllAnnotationMembersAndTheirDefaults() {
        AnnotationProxy<Options> proxy = AnnotationProxy.newProxy(Options.class);

        assertThat(proxy.getProxedAnnotation().count()).isEqualTo(3);
        assertThat(proxy.getProxedAnnotation().name()).isEqualTo("ffi");
    }
}
