/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package COM.jrockit.reflect;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.jrockit.JRockit131Instantiator;

public class JRockit131InstantiatorTest {

    @Test
    void resolvesLegacyClassLiteralsWhenInitializingFromColdState() throws Throwable {
        resetJRockitStaticState();
        MemberAccess.reset();
        JRockitTarget.constructorCalls = 0;

        JRockit131Instantiator instantiator = new JRockit131Instantiator(JRockitTarget.class);

        Assertions.assertThat(MemberAccess.baseConstructorDeclaringClass()).isEqualTo(Object.class);
        Assertions.assertThat(MemberAccess.requestedType()).isEqualTo(JRockitTarget.class);
        Assertions.assertThat(instantiator.newInstance()).isInstanceOf(JRockitTarget.class);
    }

    @Test
    void createsInstancesThroughJRockitSerializationConstructorAdapter() {
        MemberAccess.reset();
        JRockitTarget.constructorCalls = 0;

        JRockit131Instantiator instantiator = new JRockit131Instantiator(JRockitTarget.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(MemberAccess.baseConstructorDeclaringClass()).isEqualTo(Object.class);
        Assertions.assertThat(MemberAccess.requestedType()).isEqualTo(JRockitTarget.class);
        Assertions.assertThat(instance).isInstanceOf(JRockitTarget.class);
        Assertions.assertThat(((JRockitTarget) instance).value).isEqualTo("constructed");
        Assertions.assertThat(JRockitTarget.constructorCalls).isEqualTo(1);
    }

    private static void resetJRockitStaticState() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(JRockit131Instantiator.class, MethodHandles.lookup());
        clearStaticField(lookup, "newConstructorForSerializationMethod", Method.class);
        clearStaticField(lookup, "class$java$lang$reflect$Constructor", Class.class);
        clearStaticField(lookup, "class$java$lang$Class", Class.class);
        clearStaticField(lookup, "class$java$lang$Object", Class.class);
    }

    private static void clearStaticField(MethodHandles.Lookup lookup, String fieldName, Class<?> fieldType)
        throws NoSuchFieldException, IllegalAccessException {
        VarHandle field = lookup.findStaticVarHandle(JRockit131Instantiator.class, fieldName, fieldType);
        field.set(null);
    }

    public static class JRockitTarget {
        static int constructorCalls;

        final String value;

        public JRockitTarget() {
            constructorCalls++;
            value = "constructed";
        }
    }
}

class MemberAccess {
    private static Constructor<?> baseConstructor;
    private static Class<?> requestedType;

    static Constructor<?> newConstructorForSerialization(Constructor<?> constructor, Class<?> type) throws NoSuchMethodException {
        baseConstructor = constructor;
        requestedType = type;
        return type.getConstructor(new Class<?>[0]);
    }

    static void reset() {
        baseConstructor = null;
        requestedType = null;
    }

    static Class<?> baseConstructorDeclaringClass() {
        return baseConstructor.getDeclaringClass();
    }

    static Class<?> requestedType() {
        return requestedType;
    }
}
