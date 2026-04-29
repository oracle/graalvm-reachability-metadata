/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.util.Locale;

import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.StaticFieldELResolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void formatsStaticFieldLookupFailureMessageForRequestedLocale() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = newContext();
        context.setLocale(Locale.ROOT);

        assertThatThrownBy(() -> resolver.getValue(
                context,
                new ELClass(PublicStaticVarargsBase.class),
                "missingField"))
                .isInstanceOf(PropertyNotFoundException.class)
                .hasMessageContaining("missingField")
                .hasMessageContaining(PublicStaticVarargsBase.class.getName());
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
