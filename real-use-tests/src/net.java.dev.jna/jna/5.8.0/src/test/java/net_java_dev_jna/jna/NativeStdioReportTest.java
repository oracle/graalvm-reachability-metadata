/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JNA's C stdio bindings to write and reread a UTF-8 inventory report.
 */
public class NativeStdioReportTest {

    @Test
    @Timeout(60)
    public void writesAndReadsTemporaryReportThroughNativeStream() {
        String encoding = StandardCharsets.UTF_8.name();
        Stdio stdio = Native.load(Platform.C_LIBRARY_NAME, Stdio.class,
                Map.of(Library.OPTION_STRING_ENCODING, encoding));
        Pointer stream = stdio.tmpfile();
        assertNotNull(stream, "C library must create a temporary report stream");
        try {
            String report = """
                    item,quantity
                    caf\u00e9,3
                    """;
            assertTrue(stdio.fputs(report, stream) >= 0, "Writing the report must succeed");
            // Seeking also flushes the output before switching the update stream to input.
            assertEquals(0, stdio.fseek(stream, new NativeLong(0), 0));

            byte[] line = new byte[128];
            assertNotNull(stdio.fgets(line, line.length, stream));
            assertEquals("item,quantity\n", Native.toString(line, encoding));
            assertNotNull(stdio.fgets(line, line.length, stream));
            assertEquals("caf\u00e9,3\n", Native.toString(line, encoding));

            assertNull(stdio.fgets(line, line.length, stream));
            assertNotEquals(0, stdio.feof(stream), "Reading past the report must signal EOF");
            assertEquals(0, stdio.ferror(stream), "EOF must not be mistaken for an I/O error");
        } finally {
            // fclose removes the temporary file as well as releasing the native stream.
            assertEquals(0, stdio.fclose(stream));
        }
    }

    public interface Stdio extends Library {
        Pointer tmpfile();

        int fputs(String text, Pointer stream);

        int fseek(Pointer stream, NativeLong offset, int origin);

        Pointer fgets(byte[] buffer, int capacity, Pointer stream);

        int feof(Pointer stream);

        int ferror(Pointer stream);

        int fclose(Pointer stream);
    }
}
