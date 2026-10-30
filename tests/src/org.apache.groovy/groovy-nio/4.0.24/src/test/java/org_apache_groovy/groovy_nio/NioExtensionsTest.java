/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio;

import groovy.io.FileType;
import groovy.lang.Closure;
import org.apache.groovy.nio.extensions.NioExtensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
public class NioExtensionsTest {
    @TempDir
    Path directory;

    @Test
    void writesAppendsAndReadsText() throws Exception {
        Path file = directory.resolve("notes.txt");
        String firstLine = "caf\u00e9\n";
        NioExtensions.setText(file, firstLine, "UTF-8");
        NioExtensions.append(file, "second\n", "UTF-8");
        try (StringReader reader = new StringReader("third\n")) {
            NioExtensions.append(file, reader, "UTF-8");
        }

        assertThat(NioExtensions.getText(file, "UTF-8")).isEqualTo(firstLine + "second\nthird\n");
        assertThat(NioExtensions.readLines(file, "UTF-8")).containsExactly("caf\u00e9", "second", "third");
        assertThat(NioExtensions.size(file))
                .isEqualTo((firstLine + "second\nthird\n").getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void writesUtf16ByteOrderMarkOnlyOnceWhenAppending() throws Exception {
        Path file = directory.resolve("unicode.txt");
        NioExtensions.write(file, "A\u03a9", "UTF-16LE", true);
        NioExtensions.append(file, "B", "UTF-16LE", true);

        assertThat(NioExtensions.readBytes(file))
                .containsExactly((byte) 0xff, (byte) 0xfe, (byte) 0x41, (byte) 0,
                        (byte) 0xa9, (byte) 0x03, (byte) 0x42, (byte) 0);
        assertThat(NioExtensions.getText(file, "UTF-16")).isEqualTo("A\u03a9B");
    }

    @Test
    void appendsBinaryArraysAndStreams() throws Exception {
        Path file = directory.resolve("payload.bin");
        NioExtensions.setBytes(file, new byte[]{0, 1, (byte) 0xff});
        assertThat(NioExtensions.leftShift(file, new byte[]{2, 3})).isSameAs(file);
        try (InputStream input = new ByteArrayInputStream(new byte[]{4, 5})) {
            assertThat(NioExtensions.leftShift(file, input)).isSameAs(file);
        }

        assertThat(NioExtensions.getBytes(file)).isEqualTo(new byte[]{0, 1, (byte) 0xff, 2, 3, 4, 5});
        assertThat(NioExtensions.size(file)).isEqualTo(7);
    }

    @Test
    void createsReadersAndAppendingWriters() throws Exception {
        Path file = directory.resolve("writer.txt");
        try (BufferedWriter writer = NioExtensions.newWriter(file, "UTF-8")) {
            writer.write("north\n");
        }
        try (BufferedWriter writer = NioExtensions.newWriter(file, "UTF-8", true)) {
            writer.write("south\n");
        }
        try (BufferedReader reader = NioExtensions.newReader(file, "UTF-8")) {
            assertThat(reader.readLine()).isEqualTo("north");
            assertThat(reader.readLine()).isEqualTo("south");
            assertThat(reader.readLine()).isNull();
        }
    }

    @Test
    void createsBufferedBinaryStreams() throws Exception {
        Path file = directory.resolve("stream.bin");
        byte[] payload = new byte[]{10, 20, 30, (byte) 0xff};
        try (OutputStream output = NioExtensions.newOutputStream(file)) {
            output.write(payload);
        }
        try (InputStream input = NioExtensions.newInputStream(file)) {
            assertThat(input.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    void roundTripsTypedDataStreams() throws Exception {
        Path file = directory.resolve("records.bin");
        try (DataOutputStream output = NioExtensions.newDataOutputStream(file)) {
            output.writeInt(42);
            output.writeUTF("record-\u03a9");
            output.writeBoolean(true);
        }
        try (DataInputStream input = NioExtensions.newDataInputStream(file)) {
            assertThat(input.readInt()).isEqualTo(42);
            assertThat(input.readUTF()).isEqualTo("record-\u03a9");
            assertThat(input.readBoolean()).isTrue();
            assertThat(input.read()).isEqualTo(-1);
        }
    }

    @Test
    void listsFilesAndRecursesThroughSubdirectories() throws Exception {
        Path top = Files.writeString(directory.resolve("top.txt"), "top");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path child = Files.writeString(nested.resolve("child.txt"), "child");
        List<Path> files = new ArrayList<>();
        NioExtensions.eachFile(directory, FileType.FILES, collecting(files));
        assertThat(files).containsExactly(top);

        List<Path> directories = new ArrayList<>();
        NioExtensions.eachDir(directory, collecting(directories));
        assertThat(directories).containsExactly(nested);

        List<Path> recursive = new ArrayList<>();
        NioExtensions.eachFileRecurse(directory, FileType.FILES, collecting(recursive));
        assertThat(recursive).containsExactlyInAnyOrder(top, child);
    }

    @Test
    void matchesFileNamesAndFiltersTraversalDepth() throws Exception {
        Path top = Files.writeString(directory.resolve("top.txt"), "top");
        Files.writeString(directory.resolve("ignore.log"), "ignored");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path child = Files.writeString(nested.resolve("child.txt"), "child");
        Pattern textFiles = Pattern.compile(".*\\.txt");
        List<Path> matches = new ArrayList<>();
        NioExtensions.eachFileMatch(directory, FileType.FILES, textFiles, collecting(matches));
        assertThat(matches).containsExactly(top);

        Map<String, Object> options = new HashMap<>();
        options.put("type", FileType.FILES);
        options.put("nameFilter", textFiles);
        options.put("maxDepth", 0);
        List<Path> shallow = new ArrayList<>();
        NioExtensions.traverse(directory, options, collecting(shallow));
        assertThat(shallow).containsExactly(top);

        options.put("maxDepth", -1);
        List<Path> recursive = new ArrayList<>();
        NioExtensions.traverse(directory, options, collecting(recursive));
        assertThat(recursive).containsExactlyInAnyOrder(top, child);
    }

    @Test
    void createsParentsRenamesAndDeletesDirectoryTrees() throws Exception {
        Path root = directory.resolve("tree");
        Path file = root.resolve("nested").resolve("original.txt");
        assertThat(NioExtensions.createParentDirectories(file)).isSameAs(file);
        NioExtensions.write(file, "move me");
        Path renamed = file.resolveSibling("renamed.txt");
        assertThat(NioExtensions.renameTo(file, renamed.toString())).isTrue();
        assertThat(Files.exists(file)).isFalse();
        assertThat(NioExtensions.getText(renamed)).isEqualTo("move me");

        Path finalPath = renamed.resolveSibling("final.txt");
        assertThat(NioExtensions.renameTo(renamed, finalPath.toUri())).isTrue();
        assertThat(NioExtensions.getText(finalPath)).isEqualTo("move me");
        assertThat(NioExtensions.deleteDir(root)).isTrue();
        assertThat(Files.exists(root)).isFalse();
    }

    private static Closure<Void> collecting(List<Path> paths) {
        return new Closure<Void>(null) {
            @Override
            public Void call(Object argument) {
                paths.add((Path) argument);
                return null;
            }
        };
    }
}
