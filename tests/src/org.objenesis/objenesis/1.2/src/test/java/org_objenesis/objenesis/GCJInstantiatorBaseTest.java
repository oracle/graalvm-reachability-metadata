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
import org.objenesis.instantiator.gcj.GCJInstantiatorBase;

public class GCJInstantiatorBaseTest {

    @Test
    void initializesGcjObjectInputStreamLookupWhenConstructed() throws Throwable {
        TestGCJInstantiator.resetInitializationState();

        Assertions.assertThatThrownBy(() -> new TestGCJInstantiator(Object.class))
            .isInstanceOf(ObjenesisException.class)
            .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void resolvesLegacyClassLiteralByRuntimeName() throws Throwable {
        String typeName = new String(RuntimeResolvedType.class.getName().toCharArray());

        Class<?> resolvedType = TestGCJInstantiator.resolveLegacyClassLiteral(typeName);

        Assertions.assertThat(resolvedType).isEqualTo(RuntimeResolvedType.class);
    }

    private static final class TestGCJInstantiator extends GCJInstantiatorBase {

        private TestGCJInstantiator(Class<?> type) {
            super(type);
        }

        @Override
        public Object newInstance() {
            return null;
        }

        private static void resetInitializationState()
            throws NoSuchFieldException, IllegalAccessException {
            newObjectMethod = null;
            dummyStream = null;
            clearCachedClassLiteral("class$java$io$ObjectInputStream");
            clearCachedClassLiteral("class$java$lang$Class");
        }

        private static Class<?> resolveLegacyClassLiteral(String typeName) throws Throwable {
            MethodHandle classLiteralResolver = gcjInstantiatorBaseLookup().findStatic(
                GCJInstantiatorBase.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
            );
            return (Class<?>) classLiteralResolver.invoke(typeName);
        }

        private static void clearCachedClassLiteral(String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
            VarHandle field = gcjInstantiatorBaseLookup().findStaticVarHandle(
                GCJInstantiatorBase.class,
                fieldName,
                Class.class
            );
            field.set(null);
        }

        private static MethodHandles.Lookup gcjInstantiatorBaseLookup()
            throws IllegalAccessException {
            return MethodHandles.privateLookupIn(GCJInstantiatorBase.class, MethodHandles.lookup());
        }
    }

    public static final class RuntimeResolvedType {
    }
}
