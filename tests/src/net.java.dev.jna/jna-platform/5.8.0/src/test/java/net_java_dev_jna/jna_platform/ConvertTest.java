/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.COM.util.IComEnum;
import com.sun.jna.platform.win32.COM.util.ObjectFactory;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinDef;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ConvertTest {

    @Test
    void toVariantUsesMatchingVariantConstructorForJnaValueTypes() throws Exception {
        Method toVariant = convertClass().getDeclaredMethod("toVariant", Object.class);
        toVariant.setAccessible(true);

        VARIANT variant = (VARIANT) toVariant.invoke(null, new WinDef.BYTE(42));

        assertThat(variant.getVarType().intValue()).isEqualTo(Variant.VT_UI1);
        assertThat(variant.byteValue()).isEqualTo((byte) 42);
    }

    @Test
    void toJavaObjectMapsVariantNumbersToComEnums() throws Exception {
        Method toJavaObject = convertClass().getDeclaredMethod(
            "toJavaObject",
            VARIANT.class,
            Class.class,
            ObjectFactory.class,
            boolean.class,
            boolean.class
        );
        toJavaObject.setAccessible(true);

        Object result = toJavaObject.invoke(
            null,
            new VARIANT(SampleComEnum.SELECTED.getValue()),
            SampleComEnum.class,
            null,
            false,
            false
        );

        assertThat(result).isEqualTo(SampleComEnum.SELECTED);
    }

    private static Class<?> convertClass() throws ClassNotFoundException {
        return Class.forName("com.sun.jna.platform.win32.COM.util.Convert");
    }

    public enum SampleComEnum implements IComEnum {
        IGNORED(11),
        SELECTED(42);

        private final long value;

        SampleComEnum(long value) {
            this.value = value;
        }

        @Override
        public long getValue() {
            return value;
        }
    }

}
