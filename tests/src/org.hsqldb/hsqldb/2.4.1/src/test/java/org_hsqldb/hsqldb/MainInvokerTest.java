/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainInvokerTest {
    @TempDir
    Path directory;

    @Test
    void dispatchesACommandWithItsArguments() throws Exception {
        Path input = directory.resolve("input.txt");
        Path output = directory.resolve("output.txt");
        Files.writeString(input, "command payload");
        MainInvoker.invoke("org_hsqldb.hsqldb.MainInvokerTest$CopyCommand",
                new String[]{input.toString(), output.toString()});
        assertEquals("command payload", Files.readString(output));
    }

    public static class CopyCommand {
        public static void main(String[] arguments) throws Exception {
            Files.copy(Path.of(arguments[0]), Path.of(arguments[1]));
        }
    }
}
