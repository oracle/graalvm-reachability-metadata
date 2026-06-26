/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinDef.BYTE;
import com.sun.jna.platform.win32.COM.util.IComEnum;

public class ConvertTest {
    @Test
    void toVariantUsesMatchingVariantConstructorForWin32ValueTypes() throws Exception {
        VARIANT variant = (VARIANT) convertMethod("toVariant", Object.class).invoke(null, new BYTE(9));

        assertThat(variant).isNotNull();
        assertThat(variant.getVarType().intValue()).isEqualTo(Variant.VT_UI1);
        assertThat(variant.byteValue()).isEqualTo((byte) 9);
    }

    @Test
    void toComEnumUsesEnumValuesMethodToSelectMatchingValue() throws Exception {
        TestComChoice choice = (TestComChoice) convertMethod("toComEnum", Class.class, Object.class)
                .invoke(null, TestComChoice.class, TestComChoice.SECOND.getValue());

        assertThat(choice).isEqualTo(TestComChoice.SECOND);
    }

    private static Method convertMethod(String name, Class<?>... parameterTypes) throws Exception {
        Class<?> convertClass = Class.forName("com.sun.jna.platform.win32.COM.util.Convert");
        Method method = convertClass.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    public enum TestComChoice implements IComEnum {
        FIRST(1),
        SECOND(2);

        private final long value;

        TestComChoice(long value) {
            this.value = value;
        }

        @Override
        public long getValue() {
            return value;
        }
    }
}
