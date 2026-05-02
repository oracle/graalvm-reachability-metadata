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
    void invokesSingletonAspectLookupMethods() {
        Object aspect = Aspects14.aspectOf(SingletonAspect.class);
        boolean hasAspect = Aspects14.hasAspect(SingletonAspect.class);

        assertThat(aspect).isSameAs(SingletonAspect.INSTANCE);
        assertThat(hasAspect).isTrue();
    }

    @Test
    void invokesPerObjectAspectLookupMethods() {
        Object target = new Object();

        Object aspect = Aspects14.aspectOf(PerObjectAspect.class, target);
        boolean hasAspect = Aspects14.hasAspect(PerObjectAspect.class, target);
        boolean hasAspectForNullTarget = Aspects14.hasAspect(PerObjectAspect.class, (Object) null);

        assertThat(aspect).isInstanceOfSatisfying(PerObjectAspect.class,
                perObjectAspect -> assertThat(perObjectAspect.target()).isSameAs(target));
        assertThat(hasAspect).isTrue();
        assertThat(hasAspectForNullTarget).isFalse();
    }

    @Test
    void invokesPerTypeWithinAspectLookupMethods() {
        Object aspect = Aspects14.aspectOf(PerTypeWithinAspect.class, String.class);
        boolean hasAspect = Aspects14.hasAspect(PerTypeWithinAspect.class, String.class);
        boolean hasAspectForDifferentType = Aspects14.hasAspect(PerTypeWithinAspect.class, Integer.class);

        assertThat(aspect).isInstanceOfSatisfying(PerTypeWithinAspect.class,
                perTypeWithinAspect -> assertThat(perTypeWithinAspect.type()).isEqualTo(String.class));
        assertThat(hasAspect).isTrue();
        assertThat(hasAspectForDifferentType).isFalse();
    }

    public static class SingletonAspect {
        private static final SingletonAspect INSTANCE = new SingletonAspect();

        public static SingletonAspect aspectOf() {
            return INSTANCE;
        }

        public static boolean hasAspect() {
            return true;
        }
    }

    public static class PerObjectAspect {
        private final Object target;

        private PerObjectAspect(Object target) {
            this.target = target;
        }

        public static PerObjectAspect aspectOf(Object target) {
            return new PerObjectAspect(target);
        }

        public static boolean hasAspect(Object target) {
            return target != null;
        }

        public Object target() {
            return target;
        }
    }

    public static class PerTypeWithinAspect {
        private final Class<?> type;

        private PerTypeWithinAspect(Class<?> type) {
            this.type = type;
        }

        public static PerTypeWithinAspect aspectOf(Class<?> type) {
            return new PerTypeWithinAspect(type);
        }

        public static boolean hasAspect(Class<?> type) {
            return String.class.equals(type);
        }

        public Class<?> type() {
            return type;
        }
    }
}
