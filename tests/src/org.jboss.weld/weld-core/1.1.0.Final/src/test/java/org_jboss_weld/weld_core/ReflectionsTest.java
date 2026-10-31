/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.weld.util.reflection.Reflections;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {
    @Test
    void identifiesFinalMethodsThatPreventProxying() {
        Object blockingMember = Reflections.getNonPrivateFinalMethodOrType(FinalMethodBean.class);
        assertThat(blockingMember).isInstanceOf(Method.class);
        assertThat(((Method) blockingMember).getName()).isEqualTo("fixed");
        assertThat(Reflections.isTypeOrAnyMethodFinal(FinalMethodBean.class)).isTrue();
        assertThat(Reflections.getNonPrivateFinalMethodOrType(PrivateFinalBean.class)).isNull();
        assertThat(Reflections.isTypeOrAnyMethodFinal(ReflectionFixture.class)).isFalse();
        assertThat(Reflections.getNonPrivateFinalMethodOrType(String.class)).isEqualTo(String.class);
    }

    public static class FinalMethodBean {
        public final String fixed() {
            return "fixed";
        }
    }

    public static class PrivateFinalBean {
        private final String fixed() {
            return "private";
        }
    }
}
