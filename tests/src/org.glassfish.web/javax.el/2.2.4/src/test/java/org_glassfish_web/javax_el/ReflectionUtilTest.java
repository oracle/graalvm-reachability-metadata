/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;
import com.sun.el.util.ReflectionUtil;

public class ReflectionUtilTest {
    @Test
    void forNameLoadsClassByName() throws Exception {
        Class<?> type = ReflectionUtil.forName("com.sun.el.ExpressionFactoryImpl");

        assertThat(type).isEqualTo(ExpressionFactoryImpl.class);
    }

    @Test
    void forNameCreatesArrayClassFromComponentName() throws Exception {
        Class<?> type = ReflectionUtil.forName("com.sun.el.ExpressionFactoryImpl[]");

        assertThat(type).isEqualTo(ExpressionFactoryImpl[].class);
        assertThat(type.getComponentType()).isEqualTo(ExpressionFactoryImpl.class);
    }

    @Test
    void getMethodFindsPublicMethodWithExactParameterTypes() {
        assertThat(ReflectionUtil.getMethod(new ExpressionFactoryImpl(), "coerceToType",
                new Class<?>[] {Object.class, Class.class})).isNotNull();
    }

    @Test
    void invokeMethodFindsPublicMethodAndInvokesItWithCoercedParameters() {
        Object result = ReflectionUtil.invokeMethod(new ExpressionFactoryImpl(), "coerceToType",
                new Object[] {"42", Integer.class});

        assertThat(result).isEqualTo(42);
    }
}
