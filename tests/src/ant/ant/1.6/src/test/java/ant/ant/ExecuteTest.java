/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import org.apache.tools.ant.taskdefs.Execute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecuteTest {
    @Test
    void resolvesCompilerGeneratedClassLiteralsUsedByVmLauncher() throws Throwable {
        ExposedExecute.clearCachedClassLiterals();

        assertThat(ExposedExecute.lookupCompilerGeneratedClass(Runtime.class.getName()))
                .isSameAs(Runtime.class);
        assertThat(ExposedExecute.lookupCompilerGeneratedClass(String[].class.getName()))
                .isSameAs(String[].class);
        assertThat(ExposedExecute.lookupCompilerGeneratedClass(File.class.getName()))
                .isSameAs(File.class);
    }

    private static final class ExposedExecute {
        private static final MethodHandle CLASS_LOOKUP = classLookupMethod();
        private static final VarHandle RUNTIME_CLASS = staticField(
                "class$java$lang$Runtime",
                Class.class);
        private static final VarHandle STRING_ARRAY_CLASS = staticField(
                "array$Ljava$lang$String",
                Class.class);
        private static final VarHandle FILE_CLASS = staticField(
                "class$java$io$File",
                Class.class);

        static Class<?> lookupCompilerGeneratedClass(String className) throws Throwable {
            return (Class<?>) CLASS_LOOKUP.invoke(className);
        }

        static void clearCachedClassLiterals() {
            RUNTIME_CLASS.set(null);
            STRING_ARRAY_CLASS.set(null);
            FILE_CLASS.set(null);
        }

        private static MethodHandle classLookupMethod() {
            try {
                return MethodHandles.privateLookupIn(Execute.class, MethodHandles.lookup())
                        .findStatic(
                                Execute.class,
                                "class$",
                                MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return MethodHandles.privateLookupIn(Execute.class, MethodHandles.lookup())
                        .findStaticVarHandle(Execute.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }
}
