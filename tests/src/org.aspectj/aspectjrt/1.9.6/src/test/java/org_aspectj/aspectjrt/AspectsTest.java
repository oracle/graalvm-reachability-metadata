/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;

public class AspectsTest {
    @Test
    void resolvesAndInvokesAspectFactoriesForAllScopingStyles() {
        Object perObject = new Object();

        ScopedAspect singletonAspect = Aspects.aspectOf(ScopedAspect.class);
        ScopedAspect objectAspect = Aspects.aspectOf(ScopedAspect.class, perObject);
        ScopedAspect typeAspect = Aspects.aspectOf(ScopedAspect.class, String.class);

        assertThat(singletonAspect.scope()).isEqualTo("singleton");
        assertThat(objectAspect.scope()).isEqualTo("object:" + perObject.getClass().getName());
        assertThat(typeAspect.scope()).isEqualTo("type:" + String.class.getName());
    }

    @Test
    void resolvesAndInvokesHasAspectChecksForAllScopingStyles() {
        assertThat(Aspects.hasAspect(ScopedAspect.class)).isTrue();
        assertThat(Aspects.hasAspect(ScopedAspect.class, new Object())).isTrue();
        assertThat(Aspects.hasAspect(ScopedAspect.class, String.class)).isTrue();
    }

    public static class ScopedAspect {
        private final String scope;

        private ScopedAspect(String scope) {
            this.scope = scope;
        }

        public static ScopedAspect aspectOf() {
            return new ScopedAspect("singleton");
        }

        public static ScopedAspect aspectOf(Object perObject) {
            return new ScopedAspect("object:" + perObject.getClass().getName());
        }

        public static ScopedAspect aspectOf(Class<?> perTypeWithin) {
            return new ScopedAspect("type:" + perTypeWithin.getName());
        }

        public static boolean hasAspect() {
            return true;
        }

        public static boolean hasAspect(Object perObject) {
            return perObject != null;
        }

        public static boolean hasAspect(Class<?> perTypeWithin) {
            return perTypeWithin != null;
        }

        String scope() {
            return scope;
        }
    }
}
