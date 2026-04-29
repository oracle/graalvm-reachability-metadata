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
    void createsInstantiatorFromPublicNoArgConstructor() {
        Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy();
        boolean originalIsAndroid = Util.isAndroid;

        try {
            Util.isAndroid = true;
            ObjectInstantiator instantiator = strategy.newInstantiatorOf(PublicNoArgSubject.class);
            Object created = instantiator.newInstance();

            assertThat(created).isInstanceOf(PublicNoArgSubject.class);
            assertThat(((PublicNoArgSubject) created).value).isEqualTo("public-constructor");
        } finally {
            Util.isAndroid = originalIsAndroid;
        }
    }

    @Test
    void createsInstantiatorFromDeclaredNoArgConstructor() {
        Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy();
        boolean originalIsAndroid = Util.isAndroid;

        try {
            Util.isAndroid = true;
            ObjectInstantiator instantiator = strategy.newInstantiatorOf(PrivateNoArgSubject.class);
            Object created = instantiator.newInstance();

            assertThat(created).isInstanceOf(PrivateNoArgSubject.class);
            assertThat(((PrivateNoArgSubject) created).value).isEqualTo("declared-constructor");
        } finally {
            Util.isAndroid = originalIsAndroid;
        }
    }

    public static class PublicNoArgSubject {
        final String value;

        public PublicNoArgSubject() {
            this.value = "public-constructor";
        }
    }

    private static class PrivateNoArgSubject {
        final String value;

        private PrivateNoArgSubject() {
            this.value = "declared-constructor";
        }
    }
}
