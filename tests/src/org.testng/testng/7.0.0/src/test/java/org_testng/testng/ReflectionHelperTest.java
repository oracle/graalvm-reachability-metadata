/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.testng.internal.reflect.ReflectionHelper;

public class ReflectionHelperTest {
    @Test
    void includesDefaultInterfaceMethodsAsLocalMethods() {
        Method[] localMethods = ReflectionHelper.getLocalMethods(DefaultMethodTestCase.class);

        assertThat(localMethods)
                .filteredOn(method -> method.getDeclaringClass().equals(DefaultMethodContract.class))
                .extracting(Method::getName)
                .containsExactly("providedByInterface");
    }

    public interface DefaultMethodContract {
        default String providedByInterface() {
            return "default method";
        }
    }

    public static final class DefaultMethodTestCase implements DefaultMethodContract {
        public void declaredMethod() {
        }
    }
}
