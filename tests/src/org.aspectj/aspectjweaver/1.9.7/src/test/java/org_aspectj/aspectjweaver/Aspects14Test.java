/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.Aspects14;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Aspects14Test {
    @Test
    void invokesAspectOfFactoriesForAllLegacyAspectLifecycles() {
        LegacyAspect singletonAspect = (LegacyAspect) Aspects14.aspectOf(LegacyAspect.class);
        LegacyAspect perObjectAspect = (LegacyAspect) Aspects14.aspectOf(
                LegacyAspect.class, new TargetObject("perObject"));
        LegacyAspect perTypeWithinAspect = (LegacyAspect) Aspects14.aspectOf(LegacyAspect.class, TargetObject.class);

        assertThat(singletonAspect.description()).isEqualTo("singleton");
        assertThat(perObjectAspect.description()).isEqualTo("per-object:perObject");
        assertThat(perTypeWithinAspect.description()).isEqualTo("per-type:" + TargetObject.class.getName());
    }

    @Test
    void invokesHasAspectChecksForAllLegacyAspectLifecycles() {
        assertThat(Aspects14.hasAspect(LegacyAspect.class)).isTrue();
        assertThat(Aspects14.hasAspect(LegacyAspect.class, new TargetObject("bound"))).isTrue();
        assertThat(Aspects14.hasAspect(LegacyAspect.class, new TargetObject("unbound"))).isFalse();
        assertThat(Aspects14.hasAspect(LegacyAspect.class, TargetObject.class)).isTrue();
        assertThat(Aspects14.hasAspect(LegacyAspect.class, String.class)).isFalse();
    }

    public static class LegacyAspect {
        private final String description;

        public LegacyAspect(String description) {
            this.description = description;
        }

        public static LegacyAspect aspectOf() {
            return new LegacyAspect("singleton");
        }

        public static LegacyAspect aspectOf(Object perObject) {
            TargetObject targetObject = (TargetObject) perObject;
            return new LegacyAspect("per-object:" + targetObject.name());
        }

        public static LegacyAspect aspectOf(Class<?> perTypeWithin) {
            return new LegacyAspect("per-type:" + perTypeWithin.getName());
        }

        public static boolean hasAspect() {
            return true;
        }

        public static boolean hasAspect(Object perObject) {
            TargetObject targetObject = (TargetObject) perObject;
            return "bound".equals(targetObject.name());
        }

        public static boolean hasAspect(Class<?> perTypeWithin) {
            return TargetObject.class.equals(perTypeWithin);
        }

        public String description() {
            return description;
        }
    }

    public record TargetObject(String name) {
    }
}
