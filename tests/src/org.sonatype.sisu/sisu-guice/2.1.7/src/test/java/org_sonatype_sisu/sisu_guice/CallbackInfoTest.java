/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.internal.cglib.proxy.Callback;
import com.google.inject.internal.cglib.proxy.Enhancer;
import com.google.inject.internal.cglib.proxy.MethodInterceptor;
import com.google.inject.internal.cglib.proxy.MethodProxy;
import com.google.inject.internal.cglib.proxy.NoOp;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class CallbackInfoTest {
    @Test
    void validatesCallbackTypesBeforeRequiringCallbackFilter() {
        Enhancer enhancer = new Enhancer();

        enhancer.setCallbacks(new Callback[] {
                NoOp.INSTANCE,
                new StaticMethodInterceptor()
        });

        assertThatThrownBy(enhancer::create)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple callback types possible but no filter specified");
    }

    private static final class StaticMethodInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(
                Object object, Method method, Object[] arguments, MethodProxy methodProxy) {
            return null;
        }
    }
}
