/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import com.thoughtworks.qdox.tools.QDoxTester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class QDoxTesterTest {
    @Test
    @SuppressWarnings("removal")
    void mainWithoutInputsPrintsUsageAndUsesCompilerGeneratedLookup() throws Throwable {
        resolveReporterInterfaceWithCompilerGeneratedLookup();

        boolean[] mainInvoked = {false};
        ByteArrayOutputStream errorOutput = captureStandardError(() -> {
            SecurityManager originalSecurityManager = System.getSecurityManager();
            ExitCatchingSecurityManager exitCatchingSecurityManager = new ExitCatchingSecurityManager();
            boolean installed = installSecurityManagerIfSupported(exitCatchingSecurityManager);

            try {
                if (installed) {
                    mainInvoked[0] = true;
                    clearTesterClassCache();
                    Throwable thrown = catchThrowable(() -> QDoxTester.main(new String[0]));
                    assertThat(thrown).isInstanceOf(ExitException.class);
                    assertThat(((ExitException) thrown).status()).isEqualTo(-1);
                } else {
                    resolveReporterInterfaceWithCompilerGeneratedLookup();
                }
            } finally {
                if (installed) {
                    System.setSecurityManager(originalSecurityManager);
                }
            }
        });

        String usage = errorOutput.toString(StandardCharsets.UTF_8);
        if (mainInvoked[0]) {
            assertThat(usage)
                    .contains("Tool that verifies that QDox can parse some Java source.")
                    .contains("Usage: java com.thoughtworks.qdox.tools.QDoxTester")
                    .contains("Each src can be a single .java file");
        } else {
            assertThat(usage).isEmpty();
        }
    }

    private static Class<?> resolveReporterInterfaceWithCompilerGeneratedLookup() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(QDoxTester.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                QDoxTester.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        byte[] classNameBytes = (QDoxTester.class.getName() + "$Reporter").getBytes(StandardCharsets.UTF_8);
        String className = new String(classNameBytes, StandardCharsets.UTF_8);
        Class<?> reporterClass = (Class<?>) classLookup.invokeExact(className);

        assertThat(reporterClass.getName()).isEqualTo(className);
        return reporterClass;
    }

    private static void clearTesterClassCache() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(QDoxTester.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                QDoxTester.class,
                "class$com$thoughtworks$qdox$tools$QDoxTester",
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

    private static ByteArrayOutputStream captureStandardError(ThrowingRunnable action) {
        PrintStream originalError = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setErr(capture);
            action.run();
            return output;
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        } finally {
            System.setErr(originalError);
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
