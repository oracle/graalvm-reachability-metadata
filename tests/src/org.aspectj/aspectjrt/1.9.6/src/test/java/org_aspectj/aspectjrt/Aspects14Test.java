/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.Aspects14;
import org.junit.jupiter.api.Test;

public class Aspects14Test {
    @Test
    void resolvesAndInvokesAspectFactoriesForAllScopingStyles() {
        Object perObject = new Object();

        LegacyScopedAspect singletonAspect = (LegacyScopedAspect) Aspects14.aspectOf(LegacyScopedAspect.class);
        LegacyScopedAspect objectAspect = (LegacyScopedAspect) Aspects14.aspectOf(LegacyScopedAspect.class, perObject);
        LegacyScopedAspect typeAspect = (LegacyScopedAspect) Aspects14.aspectOf(LegacyScopedAspect.class, String.class);

        assertThat(singletonAspect.scope()).isEqualTo("singleton");
        assertThat(objectAspect.scope()).isEqualTo("object:" + perObject.getClass().getName());
        assertThat(typeAspect.scope()).isEqualTo("type:" + String.class.getName());
    }

    @Test
    void resolvesAndInvokesHasAspectChecksForAllScopingStyles() {
        assertThat(Aspects14.hasAspect(LegacyScopedAspect.class)).isTrue();
        assertThat(Aspects14.hasAspect(LegacyScopedAspect.class, new Object())).isTrue();
        assertThat(Aspects14.hasAspect(LegacyScopedAspect.class, String.class)).isTrue();
    }

    public static class LegacyScopedAspect {
        private final String scope;

        private LegacyScopedAspect(String scope) {
            this.scope = scope;
        }

        public static LegacyScopedAspect aspectOf() {
            return new LegacyScopedAspect("singleton");
        }

        public static LegacyScopedAspect aspectOf(Object perObject) {
            return new LegacyScopedAspect("object:" + perObject.getClass().getName());
        }

        public static LegacyScopedAspect aspectOf(Class perTypeWithin) {
            return new LegacyScopedAspect("type:" + perTypeWithin.getName());
        }

        public static boolean hasAspect() {
            return true;
        }

        public static boolean hasAspect(Object perObject) {
            return perObject != null;
        }

        public static boolean hasAspect(Class perTypeWithin) {
            return perTypeWithin != null;
        }

        String scope() {
            return scope;
        }
    }
}
