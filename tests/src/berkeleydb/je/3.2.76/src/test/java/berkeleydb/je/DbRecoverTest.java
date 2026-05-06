/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.util.DbRecover;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class DbRecoverTest {

    @Test
    void compilerGeneratedClassLookupResolvesDbRecoverClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRecover.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                DbRecover.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        String defaultClassName = new String(
                "com.sleepycat.je.util.DbRecover".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        String className = System.getProperty("berkeleydb.je.DbRecoverTest.className", defaultClassName);
        Class<?> resolvedClass = (Class<?>) classLookup.invokeExact(className);

        assertThat(resolvedClass).isEqualTo(DbRecover.class);
    }

    @Test
    @SuppressWarnings("removal")
    void mainWithoutRequiredArgumentsPrintsUsageBeforeExiting() {
        ByteArrayOutputStream output = captureStandardOut(() -> {
            SecurityManager originalSecurityManager = System.getSecurityManager();
            ExitCatchingSecurityManager exitCatchingSecurityManager = new ExitCatchingSecurityManager();
            boolean installed = installSecurityManagerIfSupported(exitCatchingSecurityManager);
            if (!installed) {
                invokeUsageWithClearedClassCache();
                return;
            }

            try {
                clearDbRecoverClassCache();
                Throwable thrown = catchThrowable(() -> DbRecover.main(new String[0]));
                assertThat(thrown).isInstanceOf(ExitException.class);
                assertThat(((ExitException) thrown).status()).isEqualTo(1);
            } finally {
                System.setSecurityManager(originalSecurityManager);
            }
        });

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Usage:")
                .contains("com.sleepycat.je.util.DbRecover")
                .contains("-h <environment home>")
                .contains("-f <file number, in hex>")
                .contains("-o <offset, in hex>");
    }

    @Test
    void usageMessageResolvesRecoverUtilityCommandClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRecover.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                DbRecover.class,
                "class$com$sleepycat$je$util$DbRecover",
                Class.class);
        MethodHandle usage = lookup.findStatic(DbRecover.class, "usage", MethodType.methodType(void.class));
        classCache.set((Class<?>) null);

        ByteArrayOutputStream output = captureStandardOut(() -> {
            usage.invokeExact();
        });

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Usage:")
                .contains("com.sleepycat.je.util.DbRecover")
                .contains("-h <environment home>")
                .contains("-f <file number, in hex>")
                .contains("-o <offset, in hex>");
    }

    private static void invokeUsageWithClearedClassCache() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRecover.class, MethodHandles.lookup());
        MethodHandle usage = lookup.findStatic(DbRecover.class, "usage", MethodType.methodType(void.class));
        clearDbRecoverClassCache();
        usage.invokeExact();
    }

    private static void clearDbRecoverClassCache() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRecover.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                DbRecover.class,
                "class$com$sleepycat$je$util$DbRecover",
                Class.class);
        classCache.set((Class<?>) null);
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException | SecurityException unsupported) {
            return false;
        }
    }

    private static ByteArrayOutputStream captureStandardOut(ThrowingRunnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);
            action.run();
            return output;
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        } finally {
            System.setOut(originalOut);
            capture.close();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @SuppressWarnings("removal")
    private static final class ExitCatchingSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkExit(int status) {
            throw new ExitException(status);
        }
    }

    private static final class ExitException extends SecurityException {
        private final int status;

        private ExitException(int status) {
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
