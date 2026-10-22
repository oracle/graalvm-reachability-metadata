/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JNA's variadic argument marshalling to format a receipt through native C snprintf.
 */
public class NativeReceiptFormattingTest {

    @Test
    @Timeout(60)
    public void formatsUtf8ReceiptAfterGrowingTheNativeOutputBuffer() {
        String encoding = StandardCharsets.UTF_8.name();
        NativeFormatter formatter = Native.load(Platform.C_LIBRARY_NAME, NativeFormatter.class,
                Map.of(Library.OPTION_STRING_ENCODING, encoding));
        String format = "order=%04d; item=%s; quantity=%d; total=%d.%02d\n";
        int quantity = 3;
        int totalCents = quantity * 1299;
        Object[] fields = {42, "caf\u00e9 beans", quantity, totalCents / 100, totalCents % 100};
        byte[] preview = new byte[32];

        // snprintf reports the required byte count even when the first buffer is too small.
        int requiredBytes = formatter.snprintf(preview, new SizeT(preview.length), format, fields);
        assertTrue(requiredBytes >= preview.length, "This receipt must trigger buffer growth");
        assertEquals(0, preview[preview.length - 1], "The partial output must remain terminated");

        byte[] receipt = new byte[requiredBytes + 1];
        assertEquals(requiredBytes, formatter.snprintf(receipt, new SizeT(receipt.length), format, fields),
                "Retrying with sufficient capacity must preserve the required receipt size");
        String expected = "order=0042; item=caf\u00e9 beans; quantity=3; total=38.97\n";
        assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, requiredBytes,
                "Buffer capacity must account for UTF-8 bytes, not Java character count");
        assertEquals(expected, Native.toString(receipt, encoding),
                "The receipt must retain the product name, quantity, and exact total");
        assertEquals(0, receipt[requiredBytes]);
    }

    public interface NativeFormatter extends Library {
        int snprintf(byte[] output, SizeT capacity, String format, Object... arguments);
    }

    // C size_t follows pointer width rather than the width of Java or C long.
    public static class SizeT extends IntegerType {
        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }
}
