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
    void invokesSingletonAspectLookupMethods() {
        SingletonAspect aspect = Aspects.aspectOf(SingletonAspect.class);
        boolean hasAspect = Aspects.hasAspect(SingletonAspect.class);

        assertThat(aspect).isSameAs(SingletonAspect.INSTANCE);
        assertThat(hasAspect).isTrue();
    }

    @Test
    void invokesPerObjectAspectLookupMethods() {
        Object target = new Object();

        PerObjectAspect aspect = Aspects.aspectOf(PerObjectAspect.class, target);
        boolean hasAspect = Aspects.hasAspect(PerObjectAspect.class, target);
        boolean hasAspectForNullTarget = Aspects.hasAspect(PerObjectAspect.class, (Object) null);

        assertThat(aspect.target()).isSameAs(target);
        assertThat(hasAspect).isTrue();
        assertThat(hasAspectForNullTarget).isFalse();
    }

    @Test
    void invokesPerTypeWithinAspectLookupMethods() {
        PerTypeWithinAspect aspect = Aspects.aspectOf(PerTypeWithinAspect.class, String.class);
        boolean hasAspect = Aspects.hasAspect(PerTypeWithinAspect.class, String.class);
        boolean hasAspectForDifferentType = Aspects.hasAspect(PerTypeWithinAspect.class, Integer.class);

        assertThat(aspect.type()).isEqualTo(String.class);
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
