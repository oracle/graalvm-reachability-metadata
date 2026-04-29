/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;

public class AspectsTest {
    @Test
    void invokesSingletonAspectAccessors() {
        SingletonAspect singletonAspect = Aspects.aspectOf(SingletonAspect.class);

        assertThat(singletonAspect).isSameAs(SingletonAspect.INSTANCE);
        assertThat(Aspects.hasAspect(SingletonAspect.class)).isTrue();
    }

    @Test
    void invokesPerObjectAspectAccessors() {
        Object wovenObject = new Object();

        PerObjectAspect perObjectAspect = Aspects.aspectOf(PerObjectAspect.class, wovenObject);

        assertThat(perObjectAspect.getWovenObject()).isSameAs(wovenObject);
        assertThat(Aspects.hasAspect(PerObjectAspect.class, wovenObject)).isTrue();
    }

    @Test
    void invokesPerTypeWithinAspectAccessors() {
        PerTypeWithinAspect perTypeWithinAspect = Aspects.aspectOf(PerTypeWithinAspect.class, WovenType.class);

        assertThat(perTypeWithinAspect.getWovenType()).isEqualTo(WovenType.class);
        assertThat(Aspects.hasAspect(PerTypeWithinAspect.class, WovenType.class)).isTrue();
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
        private final Object wovenObject;

        private PerObjectAspect(Object wovenObject) {
            this.wovenObject = wovenObject;
        }

        public static PerObjectAspect aspectOf(Object wovenObject) {
            return new PerObjectAspect(wovenObject);
        }

        public static boolean hasAspect(Object wovenObject) {
            return wovenObject != null;
        }

        Object getWovenObject() {
            return wovenObject;
        }
    }

    public static class PerTypeWithinAspect {
        private final Class<?> wovenType;

        private PerTypeWithinAspect(Class<?> wovenType) {
            this.wovenType = wovenType;
        }

        public static PerTypeWithinAspect aspectOf(Class<?> wovenType) {
            return new PerTypeWithinAspect(wovenType);
        }

        public static boolean hasAspect(Class<?> wovenType) {
            return WovenType.class.equals(wovenType);
        }

        Class<?> getWovenType() {
            return wovenType;
        }
    }

    public static class WovenType {
    }
}
