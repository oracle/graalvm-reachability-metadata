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
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises JNA's by-reference output parameters to parse storage quotas with native C strtol.
 */
public class NativeStorageQuotaTest {

    @Test
    @Timeout(60)
    public void convertsConfiguredQuotasToBytesAndRejectsMalformedSettings() {
        NativeNumbers numbers = Native.load(Platform.C_LIBRARY_NAME, NativeNumbers.class);

        assertEquals(67_108_864L, parseQuota(numbers, "64MiB"));
        assertEquals(524_288L, parseQuota(numbers, "512KiB"));
        assertEquals(2_147_483_648L, parseQuota(numbers, "2GiB"));
        assertEquals(4096L, parseQuota(numbers, "4096B"));
        assertEquals(0L, parseQuota(numbers, "0MiB"), "An explicit zero quota is valid");

        assertThrows(IllegalArgumentException.class, () -> parseQuota(numbers, "MiB"),
                "A unit without a numeric magnitude must not become a zero quota");
        assertThrows(IllegalArgumentException.class, () -> parseQuota(numbers, "64MB"),
                "Ambiguous decimal units must not be interpreted as binary units");
        assertThrows(IllegalArgumentException.class, () -> parseQuota(numbers, "-1GiB"));
        assertThrows(IllegalArgumentException.class, () -> parseQuota(numbers, "1000001B"));
    }

    private static long parseQuota(NativeNumbers numbers, String setting) {
        byte[] input = Native.toByteArray(setting, StandardCharsets.US_ASCII.name());
        long allocation = Native.malloc(input.length);
        if (allocation == 0) {
            throw new OutOfMemoryError("Cannot allocate native quota text");
        }
        try {
            Pointer text = new Pointer(allocation);
            text.write(0, input, 0, input.length);
            PointerByReference end = new PointerByReference();
            long magnitude = numbers.strtol(text, end, 10).longValue();
            Pointer suffix = end.getValue();

            // strtol leaves end at the start when it cannot consume any digits.
            if (text.equals(suffix)) {
                throw new IllegalArgumentException("Quota requires a numeric magnitude");
            }
            // The application caps magnitudes below the range limit of every C long ABI.
            if (magnitude < 0 || magnitude > 1_000_000) {
                throw new IllegalArgumentException("Quota magnitude must be between 0 and 1000000");
            }
            // The returned pointer borrows the input allocation, so decode it before freeing text.
            long multiplier = switch (suffix.getString(0, StandardCharsets.US_ASCII.name())) {
                case "B" -> 1L;
                case "KiB" -> 1024L;
                case "MiB" -> 1024L * 1024;
                case "GiB" -> 1024L * 1024 * 1024;
                default -> throw new IllegalArgumentException("Quota requires B, KiB, MiB, or GiB");
            };
            return magnitude * multiplier;
        } finally {
            Native.free(allocation);
        }
    }

    public interface NativeNumbers extends Library {
        NativeLong strtol(Pointer text, PointerByReference end, int radix);
    }
}
