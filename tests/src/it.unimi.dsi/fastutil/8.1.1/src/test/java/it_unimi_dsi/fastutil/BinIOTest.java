/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.io.BinIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BinIOTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsObjectsThroughStreams() throws IOException, ClassNotFoundException {
        String[] payload = {"north", null, "south"};
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BinIO.storeObject(payload, output);
            try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
                assertArrayEquals(payload, (String[]) BinIO.loadObject(input));
            }
        }
    }

    @Test
    void storesAndLoadsObjectsThroughFilesAndPathnames() throws IOException, ClassNotFoundException {
        String[] payload = {"east", "west"};
        File file = tempDir.resolve("object.bin").toFile();
        BinIO.storeObject(payload, file);
        assertArrayEquals(payload, (String[]) BinIO.loadObject(file));

        CharSequence filename = tempDir.resolve("named-object.bin").toString();
        BinIO.storeObject(payload, filename);
        assertArrayEquals(payload, (String[]) BinIO.loadObject(filename));
    }
}
