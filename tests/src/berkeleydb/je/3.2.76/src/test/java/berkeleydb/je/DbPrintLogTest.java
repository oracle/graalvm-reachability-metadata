/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.util.DbPrintLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class DbPrintLogTest {

    @Test
    void usageMessageResolvesPrintLogUtilityCommandClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbPrintLog.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                DbPrintLog.class,
                "class$com$sleepycat$je$util$DbPrintLog",
                Class.class);
        MethodHandle usage = lookup.findStatic(DbPrintLog.class, "usage", MethodType.methodType(void.class));
        classCache.set((Class<?>) null);

        ByteArrayOutputStream output = captureStandardOut(() -> {
            usage.invokeExact();
        });

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Usage:")
                .contains("com.sleepycat.je.util.DbPrintLog")
                .contains("-S  show Summary of log entries");
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
}
