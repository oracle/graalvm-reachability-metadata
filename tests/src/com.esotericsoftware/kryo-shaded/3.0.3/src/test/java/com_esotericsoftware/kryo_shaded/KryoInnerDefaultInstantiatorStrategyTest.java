/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;

public class KryoInnerDefaultInstantiatorStrategyTest {
    @Test
    void createsInstanceUsingPublicNoArgumentConstructorWhenReflectAsmIsUnavailable() {
        PublicConstructorBean.created = false;

        Object instance = newInstanceWithReflectiveConstruction(PublicConstructorBean.class);

        assertThat(instance).isInstanceOf(PublicConstructorBean.class);
        assertThat(PublicConstructorBean.created).isTrue();
    }

    @Test
    void createsInstanceUsingPrivateNoArgumentConstructorWhenReflectAsmIsUnavailable() {
        PrivateConstructorBean.created = false;

        Object instance = newInstanceWithReflectiveConstruction(PrivateConstructorBean.class);

        assertThat(instance).isInstanceOf(PrivateConstructorBean.class);
        assertThat(PrivateConstructorBean.created).isTrue();
    }

    private static Object newInstanceWithReflectiveConstruction(Class<?> type) {
        boolean originalAndroidFlag = Util.isAndroid;
        Util.isAndroid = true;
        try {
            Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy();
            ObjectInstantiator instantiator = strategy.newInstantiatorOf(type);
            return instantiator.newInstance();
        } finally {
            Util.isAndroid = originalAndroidFlag;
        }
    }

    public static class PublicConstructorBean {
        private static boolean created;

        public PublicConstructorBean() {
            created = true;
        }
    }

    public static class PrivateConstructorBean {
        private static boolean created;

        private PrivateConstructorBean() {
            created = true;
        }
    }
}
