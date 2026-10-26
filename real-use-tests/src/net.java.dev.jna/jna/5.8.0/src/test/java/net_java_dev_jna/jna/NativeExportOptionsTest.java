/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises JNA's null-terminated string-array marshalling to parse export options with POSIX getsubopt.
 */
public class NativeExportOptionsTest {

    @Test
    @Timeout(60)
    public void parsesExportConfigurationAndRejectsUnsupportedOptions() {
        NativeOptions parser = Native.load(Platform.C_LIBRARY_NAME, NativeOptions.class);

        assertEquals(new ExportSettings("csv", "gzip", true),
                parse(parser, "format=csv,compression=gzip,sync"),
                "Native option parsing must preserve values and recognize a valueless flag");
        assertEquals(new ExportSettings("json", "none", false),
                parse(parser, "compression=none,format=json"),
                "Configuration must not depend on option order or inherit a previous sync flag");
        assertEquals(new ExportSettings("csv", "none", true), parse(parser, "sync"),
                "Omitted valued options must retain application defaults");

        assertThrows(IllegalArgumentException.class, () -> parse(parser, "format=csv,encrypt=aes"),
                "An unknown native option must not silently enter the export configuration");
        assertThrows(IllegalArgumentException.class, () -> parse(parser, "format"),
                "A valued option must not be accepted as a flag");
        assertThrows(IllegalArgumentException.class, () -> parse(parser, "compression="));
        assertThrows(IllegalArgumentException.class, () -> parse(parser, "sync=yes"),
                "The durability flag must not accept an ignored value");
    }

    private static ExportSettings parse(NativeOptions parser, String configuration) {
        String encoding = StandardCharsets.US_ASCII.name();
        byte[] encoded = Native.toByteArray(configuration, encoding);
        long allocation = Native.malloc(encoded.length);
        if (allocation == 0) {
            throw new OutOfMemoryError("Cannot allocate native export options");
        }
        try {
            Pointer buffer = new Pointer(allocation);
            buffer.write(0, encoded, 0, encoded.length);
            PointerByReference remaining = new PointerByReference(buffer);
            PointerByReference value = new PointerByReference();
            // JNA supplies the terminating null pointer required by getsubopt's token table.
            String[] tokens = {"format", "compression", "sync"};
            String format = "csv";
            String compression = "none";
            boolean sync = false;

            while (remaining.getValue().getByte(0) != 0) {
                // getsubopt splits the owned buffer in place and advances the remaining pointer.
                int option = parser.getsubopt(remaining, tokens, value);
                Pointer nativeValue = value.getValue();
                String argument = nativeValue == null ? null : nativeValue.getString(0, encoding);
                switch (option) {
                    case 0 -> format = requireValue(tokens[option], argument);
                    case 1 -> compression = requireValue(tokens[option], argument);
                    case 2 -> {
                        if (argument != null) {
                            throw new IllegalArgumentException("sync does not take a value");
                        }
                        sync = true;
                    }
                    default -> throw new IllegalArgumentException("Unknown export option: " + argument);
                }
            }
            return new ExportSettings(format, compression, sync);
        } finally {
            // Returned value pointers borrow this buffer; only decoded Java strings may escape.
            Native.free(allocation);
        }
    }

    private static String requireValue(String option, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return value;
    }

    private record ExportSettings(String format, String compression, boolean sync) {
    }

    public interface NativeOptions extends Library {
        int getsubopt(PointerByReference remaining, String[] tokens, PointerByReference value);
    }
}
