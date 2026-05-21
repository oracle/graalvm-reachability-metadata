/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.AbstractMemberAccess;
import ognl.AccessibleObjectHandler;
import ognl.OgnlContext;
import ognl.OgnlRuntime;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessibleObjectHandlerJDK9PlusTest {
    @Test
    void usesUnsafeToSetAccessibleObjectFlag() throws Exception {
        final Unsafe unsafe = unsafe();
        final OgnlContext context = new OgnlContext(null, null, new AllowAllMemberAccess());
        final Class<?> handlerClass = OgnlRuntime.classForName(context, "ognl.AccessibleObjectHandlerJDK9Plus");
        final Field overrideField = OgnlRuntime.getField(handlerClass, "_accessibleObjectOverrideField");
        final Field offsetField = OgnlRuntime.getField(handlerClass, "_accessibleObjectOverrideFieldOffset");
        final Field markerField = OgnlRuntime.getField(InstrumentedAccessibleObject.class, "marker");
        final Method determineOffsetMethod = determineOffsetMethod(handlerClass);

        final Object overrideFieldBase = unsafe.staticFieldBase(overrideField);
        final long overrideFieldOffset = unsafe.staticFieldOffset(overrideField);
        final Object offsetFieldBase = unsafe.staticFieldBase(offsetField);
        final long offsetFieldOffset = unsafe.staticFieldOffset(offsetField);
        final Object originalOverrideField = unsafe.getObject(overrideFieldBase, overrideFieldOffset);
        final long originalAccessibleObjectOffset = unsafe.getLong(offsetFieldBase, offsetFieldOffset);
        final boolean originalDetermineOffsetMethodAccess = determineOffsetMethod.isAccessible();

        try {
            unsafe.putObject(overrideFieldBase, overrideFieldOffset, markerField);
            determineOffsetMethod.setAccessible(true);

            final Object markerOffset = determineOffsetMethod.invoke(null);
            unsafe.putLong(offsetFieldBase, offsetFieldOffset, ((Long) markerOffset).longValue());

            final AccessibleObjectHandler handler = (AccessibleObjectHandler) unsafe.allocateInstance(handlerClass);
            final InstrumentedAccessibleObject accessibleObject = new InstrumentedAccessibleObject();
            handler.setAccessible(accessibleObject, true);

            assertThat(accessibleObject.marker).isTrue();
        } finally {
            unsafe.putObject(overrideFieldBase, overrideFieldOffset, originalOverrideField);
            unsafe.putLong(offsetFieldBase, offsetFieldOffset, originalAccessibleObjectOffset);
            determineOffsetMethod.setAccessible(originalDetermineOffsetMethodAccess);
        }
    }

    private static Method determineOffsetMethod(Class<?> handlerClass) {
        final List<?> methods = OgnlRuntime.getMethods(
                handlerClass,
                "determineAccessibleObjectOverrideFieldOffset",
                true);
        return (Method) methods.get(0);
    }

    private static Unsafe unsafe() throws Exception {
        final Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
        }
    }

    public static final class InstrumentedAccessibleObject extends AccessibleObject {
        private boolean marker;
    }
}
