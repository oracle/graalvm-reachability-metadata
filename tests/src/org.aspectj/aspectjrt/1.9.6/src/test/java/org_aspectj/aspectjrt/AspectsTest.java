/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AspectsTest {
    @Test
    void obtainsSingletonAspectAndChecksBinding() {
        SingletonAspect aspect = Aspects.aspectOf(SingletonAspect.class);

        assertThat(aspect).isSameAs(SingletonAspect.INSTANCE);
        assertThat(Aspects.hasAspect(SingletonAspect.class)).isTrue();
    }

    @Test
    void obtainsPerObjectAspectAndChecksBinding() {
        Object boundObject = PerObjectAspect.BOUND_OBJECT;
        Object unboundObject = new Object();

        PerObjectAspect aspect = Aspects.aspectOf(PerObjectAspect.class, boundObject);

        assertThat(aspect).isSameAs(PerObjectAspect.INSTANCE);
        assertThat(Aspects.hasAspect(PerObjectAspect.class, boundObject)).isTrue();
        assertThat(Aspects.hasAspect(PerObjectAspect.class, unboundObject)).isFalse();
    }

    @Test
    void obtainsPerTypeWithinAspectAndChecksBinding() {
        PerTypeWithinAspect aspect = Aspects.aspectOf(PerTypeWithinAspect.class, BoundType.class);

        assertThat(aspect).isSameAs(PerTypeWithinAspect.INSTANCE);
        assertThat(Aspects.hasAspect(PerTypeWithinAspect.class, BoundType.class)).isTrue();
        assertThat(Aspects.hasAspect(PerTypeWithinAspect.class, UnboundType.class)).isFalse();
    }

    public static class SingletonAspect {
        static final SingletonAspect INSTANCE = new SingletonAspect();

        public static SingletonAspect aspectOf() {
            return INSTANCE;
        }

        public static boolean hasAspect() {
            return true;
        }
    }

    public static class PerObjectAspect {
        static final Object BOUND_OBJECT = new Object();
        static final PerObjectAspect INSTANCE = new PerObjectAspect();

        public static PerObjectAspect aspectOf(Object perObject) {
            if (hasAspect(perObject)) {
                return INSTANCE;
            }
            throw new IllegalStateException("No aspect is bound to the supplied object");
        }

        public static boolean hasAspect(Object perObject) {
            return perObject == BOUND_OBJECT;
        }
    }

    public static class PerTypeWithinAspect {
        static final PerTypeWithinAspect INSTANCE = new PerTypeWithinAspect();

        public static PerTypeWithinAspect aspectOf(Class<?> perTypeWithin) {
            if (hasAspect(perTypeWithin)) {
                return INSTANCE;
            }
            throw new IllegalStateException("No aspect is bound to the supplied type");
        }

        public static boolean hasAspect(Class<?> perTypeWithin) {
            return perTypeWithin == BoundType.class;
        }
    }

    public static class BoundType {
    }

    public static class UnboundType {
    }
}
