/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.StaticFieldELResolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaxElUtilTest {

    @Test
    void invokesInheritedStaticVarargsMethodOnNonPublicClass() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(InheritedStaticVarargsTarget.class),
                "joinValues",
                null,
                new Object[] {"prefix", "alpha", "beta"});

        assertThat(value).isEqualTo("prefix[alpha,beta]");
    }

    private static ELContext newContext() {
        return new ELManager().getELContext();
    }

    public static class PublicStaticVarargsBase {
        public static String joinValues(String prefix, String... values) {
            return prefix + "[" + String.join(",", values) + "]";
        }
    }

    static final class InheritedStaticVarargsTarget extends PublicStaticVarargsBase {
        private InheritedStaticVarargsTarget() {
        }
    }
}
