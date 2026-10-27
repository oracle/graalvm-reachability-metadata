/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AspectsTest {
    @Test
    void invokesAspectOfFactoriesForAllAspectLifecycles() {
        SyntheticAspect singletonAspect = Aspects.aspectOf(SyntheticAspect.class);
        SyntheticAspect perObjectAspect = Aspects.aspectOf(SyntheticAspect.class, new TargetObject("perObject"));
        SyntheticAspect perTypeWithinAspect = Aspects.aspectOf(SyntheticAspect.class, TargetObject.class);

        assertThat(singletonAspect.description()).isEqualTo("singleton");
        assertThat(perObjectAspect.description()).isEqualTo("per-object:perObject");
        assertThat(perTypeWithinAspect.description()).isEqualTo("per-type:" + TargetObject.class.getName());
    }

    @Test
    void invokesHasAspectChecksForAllAspectLifecycles() {
        assertThat(Aspects.hasAspect(SyntheticAspect.class)).isTrue();
        assertThat(Aspects.hasAspect(SyntheticAspect.class, new TargetObject("bound"))).isTrue();
        assertThat(Aspects.hasAspect(SyntheticAspect.class, new TargetObject("unbound"))).isFalse();
        assertThat(Aspects.hasAspect(SyntheticAspect.class, TargetObject.class)).isTrue();
        assertThat(Aspects.hasAspect(SyntheticAspect.class, String.class)).isFalse();
    }

    public static class SyntheticAspect {
        private final String description;

        public SyntheticAspect(String description) {
            this.description = description;
        }

        public static SyntheticAspect aspectOf() {
            return new SyntheticAspect("singleton");
        }

        public static SyntheticAspect aspectOf(Object perObject) {
            TargetObject targetObject = (TargetObject) perObject;
            return new SyntheticAspect("per-object:" + targetObject.name());
        }

        public static SyntheticAspect aspectOf(Class<?> perTypeWithin) {
            return new SyntheticAspect("per-type:" + perTypeWithin.getName());
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
