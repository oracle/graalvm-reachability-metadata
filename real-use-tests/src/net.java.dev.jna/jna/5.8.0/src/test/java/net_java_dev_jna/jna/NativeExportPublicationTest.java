/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JNA's native error propagation while publishing a staged export with C rename.
 */
public class NativeExportPublicationTest {

    @Test
    @Timeout(60)
    public void publishesExportAndPreservesItWhenTheNextSourceIsMissing(@TempDir Path directory) throws IOException {
        NativeFiles files = Native.load(Platform.C_LIBRARY_NAME, NativeFiles.class);
        Path staged = directory.resolve("inventory.pending");
        Path published = directory.resolve("inventory.csv");
        String report = """
                sku,available
                BOOK-42,17
                PEN-08,64
                """;
        Files.writeString(staged, report);

        assertEquals(0, files.rename(staged.toString(), published.toString()),
                "Publishing a completed export must succeed");
        assertFalse(Files.exists(staged), "Publication must consume the staged file");
        assertEquals(report, Files.readString(published));

        // A retry with no new staged export must surface the OS failure to the application.
        LastErrorException failure = assertThrows(LastErrorException.class,
                () -> files.rename(staged.toString(), published.toString()));
        assertTrue(failure.getErrorCode() > 0, "The exception must carry the native error code");
        assertEquals(report, Files.readString(published),
                "A failed publication must leave the last complete export available");

        // Error reporting must not poison a later, valid publication on the same thread.
        Path archive = directory.resolve("inventory.archived.csv");
        assertEquals(0, files.rename(published.toString(), archive.toString()));
        assertFalse(Files.exists(published));
        assertEquals(report, Files.readString(archive));
    }

    public interface NativeFiles extends Library {
        int rename(String source, String destination) throws LastErrorException;
    }
}
