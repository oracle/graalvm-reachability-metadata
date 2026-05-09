/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.commons.el.Coercions;
import org.junit.jupiter.api.Test;

public class PrimitiveObjectsTest {
    @Test
    public void mapsPrimitiveTypesToTheirWrapperClasses() throws Throwable {
        MethodHandle getPrimitiveObjectClass = getPrimitiveObjectClassHandle();

        assertThat((Class<?>) getPrimitiveObjectClass.invoke(boolean.class))
                .isSameAs(Boolean.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(byte.class))
                .isSameAs(Byte.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(short.class))
                .isSameAs(Short.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(char.class))
                .isSameAs(Character.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(int.class))
                .isSameAs(Integer.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(long.class))
                .isSameAs(Long.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(float.class))
                .isSameAs(Float.class);
        assertThat((Class<?>) getPrimitiveObjectClass.invoke(double.class))
                .isSameAs(Double.class);
    }

    @Test
    public void leavesNonPrimitiveTypesUnchanged() throws Throwable {
        MethodHandle getPrimitiveObjectClass = getPrimitiveObjectClassHandle();

        assertThat((Class<?>) getPrimitiveObjectClass.invoke(String.class))
                .isSameAs(String.class);
    }

    private static MethodHandle getPrimitiveObjectClassHandle() throws ReflectiveOperationException {
        Class<?> primitiveObjectsClass = Coercions.class.getClassLoader()
                .loadClass("org.apache.commons.el.PrimitiveObjects");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(primitiveObjectsClass, MethodHandles.lookup());
        return lookup.findStatic(
                primitiveObjectsClass,
                "getPrimitiveObjectClass",
                MethodType.methodType(Class.class, Class.class));
    }
}
