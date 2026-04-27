/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.basic.NewInstanceInstantiator;

public class NewInstanceInstantiatorTest {

    @Test
    void usesClassNewInstanceToInvokeThePublicNoArgConstructor() {
        NewInstanceTarget.constructorCalls.set(0);

        NewInstanceInstantiator instantiator = new NewInstanceInstantiator(NewInstanceTarget.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(NewInstanceTarget.class);
        NewInstanceTarget target = (NewInstanceTarget) instance;
        Assertions.assertThat(target.value).isEqualTo("created by public constructor");
        Assertions.assertThat(NewInstanceTarget.constructorCalls).hasValue(1);
    }

    public static final class NewInstanceTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String value;

        public NewInstanceTarget() {
            constructorCalls.incrementAndGet();
            this.value = "created by public constructor";
        }
    }
}
