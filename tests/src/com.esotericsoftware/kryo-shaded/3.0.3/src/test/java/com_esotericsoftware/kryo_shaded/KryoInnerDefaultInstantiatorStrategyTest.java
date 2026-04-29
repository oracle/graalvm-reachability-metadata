/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.util.IntArray;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class KryoInnerDefaultInstantiatorStrategyTest {
    @Test
    void createsPublicNoArgLibraryTypeUsingReflectionWhenReflectAsmIsBypassed() {
        boolean previousAndroidFlag = Util.isAndroid;
        Util.isAndroid = true;
        try {
            Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy();

            @SuppressWarnings("unchecked")
            ObjectInstantiator<IntArray> instantiator = strategy.newInstantiatorOf(IntArray.class);
            IntArray array = instantiator.newInstance();

            assertThat(array).isInstanceOf(IntArray.class);
            assertThat(array.ordered).isTrue();
            assertThat(array.size).isZero();
        } finally {
            Util.isAndroid = previousAndroidFlag;
        }
    }

    @Test
    void delegatesToFallbackAfterReflectionFindsNoNoArgConstructor() {
        boolean previousAndroidFlag = Util.isAndroid;
        Util.isAndroid = true;
        try {
            Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy(new NullFallbackStrategy());

            ObjectInstantiator<?> instantiator = strategy.newInstantiatorOf(KryoSerializable.class);

            assertThat(instantiator.newInstance()).isNull();
        } finally {
            Util.isAndroid = previousAndroidFlag;
        }
    }

    private static final class NullFallbackStrategy implements InstantiatorStrategy {
        @Override
        public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
            return new ObjectInstantiator<T>() {
                @Override
                public T newInstance() {
                    return null;
                }
            };
        }
    }
}
