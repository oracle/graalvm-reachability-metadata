/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MemberAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationMemberAccessorTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void defaultMemberAccessorAccessesPrivateConstructorsMethodsAndFields() throws Exception {
        try {
            exerciseDefaultMemberAccessor();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            final Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static void exerciseDefaultMemberAccessor() throws Exception {
        final MemberAccessor accessor =
                Mockito.framework().getPlugins().getDefaultPlugin(MemberAccessor.class);
        final Constructor<PrivateComponent> constructor =
                PrivateComponent.class.getDeclaredConstructor(String.class, int.class);

        final PrivateComponent component =
                (PrivateComponent) accessor.newInstance(constructor, "initial", 7);

        final Field name = PrivateComponent.class.getDeclaredField("name");
        assertThat(accessor.get(name, component)).isEqualTo("initial");

        accessor.set(name, component, "updated");
        assertThat(accessor.get(name, component)).isEqualTo("updated");

        final Method describe = PrivateComponent.class.getDeclaredMethod("describe", String.class);
        assertThat(accessor.invoke(describe, component, "value"))
                .isEqualTo("value=updated:7");
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class PrivateComponent {
        private String name;
        private final int number;

        private PrivateComponent(String name, int number) {
            this.name = name;
            this.number = number;
        }

        private String describe(String prefix) {
            return prefix + "=" + name + ":" + number;
        }
    }
}
