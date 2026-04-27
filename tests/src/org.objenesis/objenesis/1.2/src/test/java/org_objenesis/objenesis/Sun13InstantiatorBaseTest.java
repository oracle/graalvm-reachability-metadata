/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisException;
import org.objenesis.instantiator.sun.Sun13InstantiatorBase;

public class Sun13InstantiatorBaseTest {

    @Test
    void initializesSun13ObjectInputStreamLookupWhenConstructed()
        throws ReflectiveOperationException {
        TestSun13Instantiator.resetInitializationState();

        try {
            TestSun13Instantiator instantiator = new TestSun13Instantiator(Object.class);

            Assertions.assertThat(instantiator.newInstance()).isNull();
            Assertions.assertThat(TestSun13Instantiator.initialized()).isTrue();
        }
        catch (ObjenesisException e) {
            Assertions.assertThat(e).hasCauseInstanceOf(NoSuchMethodException.class);
        }
    }

    @Test
    void resolvesLegacyClassLiteralByRuntimeName() throws Throwable {
        String typeName = new String(RuntimeResolvedType.class.getName().toCharArray());

        Class<?> resolvedType = TestSun13Instantiator.resolveLegacyClassLiteral(typeName);

        Assertions.assertThat(resolvedType).isEqualTo(RuntimeResolvedType.class);
    }

    private static final class TestSun13Instantiator extends Sun13InstantiatorBase {

        private TestSun13Instantiator(Class<?> type) {
            super(type);
        }

        @Override
        public Object newInstance() {
            return null;
        }

        private static void resetInitializationState() throws ReflectiveOperationException {
            allocateNewObjectMethod = null;
            clearCachedClassLiteral("class$java$io$ObjectInputStream");
            clearCachedClassLiteral("class$java$lang$Class");
        }

        private static Class<?> resolveLegacyClassLiteral(String typeName) throws Throwable {
            MethodHandle classLiteralResolver = sun13InstantiatorBaseLookup().findStatic(
                Sun13InstantiatorBase.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
            );
            return (Class<?>) classLiteralResolver.invoke(typeName);
        }

        private static void clearCachedClassLiteral(String fieldName)
            throws ReflectiveOperationException {
            VarHandle field = sun13InstantiatorBaseLookup()
                .findStaticVarHandle(Sun13InstantiatorBase.class, fieldName, Class.class);
            field.set(null);
        }

        private static MethodHandles.Lookup sun13InstantiatorBaseLookup()
            throws IllegalAccessException {
            return MethodHandles.privateLookupIn(
                Sun13InstantiatorBase.class,
                MethodHandles.lookup()
            );
        }

        private static boolean initialized() {
            return allocateNewObjectMethod != null;
        }
    }

    public static final class RuntimeResolvedType {
    }
}
