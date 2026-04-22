/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.COM.util.IComEnum;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ConvertTest {

    @Test
    void toVariantUsesMatchingVariantConstructorForCompatibleReferenceTypes() throws Exception {
        IntByReference reference = new IntByReference(42);

        Variant.VARIANT variant = (Variant.VARIANT) invokeConvertMethod(
                "toVariant",
                new Class<?>[]{Object.class},
                reference
        );

        assertThat(variant.getVarType().intValue()).isEqualTo(Variant.VT_BYREF | Variant.VT_INT);
        assertThat(variant.getValue()).isInstanceOf(IntByReference.class);
        assertThat(((IntByReference) variant.getValue()).getValue()).isEqualTo(42);
    }

    @Test
    void toJavaObjectRestoresComEnumsViaTheirValuesMethod() throws Exception {
        Variant.VARIANT variant = new Variant.VARIANT(SampleComEnum.STOPPED.getValue());

        Object converted = invokeConvertMethod(
                "toJavaObject",
                new Class<?>[]{Variant.VARIANT.class, Class.class, com.sun.jna.platform.win32.COM.util.ObjectFactory.class, boolean.class, boolean.class},
                variant,
                SampleComEnum.class,
                null,
                false,
                false
        );

        assertThat(converted).isEqualTo(SampleComEnum.STOPPED);
    }

    private static Object invokeConvertMethod(String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        Method method = Class.forName("com.sun.jna.platform.win32.COM.util.Convert")
                .getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    public enum SampleComEnum implements IComEnum {
        STARTED(1),
        STOPPED(2);

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
