/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import com.esotericsoftware.kryo.Kryo;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;

import java.util.function.Supplier;

final class KryoTestSupport {
    private KryoTestSupport() {
    }

    static <T> Kryo newKryoWithSpecialInstantiator(Class<T> type, Supplier<? extends T> factory) {
        Kryo kryo = new Kryo();
        Kryo.DefaultInstantiatorStrategy fallback = new Kryo.DefaultInstantiatorStrategy();
        kryo.setInstantiatorStrategy(new InstantiatorStrategy() {
            @Override
            public <S> ObjectInstantiator<S> newInstantiatorOf(Class<S> requestedType) {
                if (requestedType == type) {
                    return new ObjectInstantiator<>() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public S newInstance() {
                            return (S) factory.get();
                        }
                    };
                }
                return fallback.newInstantiatorOf(requestedType);
            }
        });
        return kryo;
    }
}
