/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_app_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.paths.DirectoryPathTree;
import io.quarkus.paths.PathFilter;

public class DirectoryPathTreeTest {
    @TempDir
    Path tempDir;

    @Test
    public void serializesAndDeserializesDirectoryTreeState() throws Exception {
        final Path root = tempDir.toAbsolutePath();
        final Path visibleFile = root.resolve("visible.txt");
        final Path hiddenFile = root.resolve("hidden.txt");
        Files.writeString(visibleFile, "visible");
        Files.writeString(hiddenFile, "hidden");
        final DirectoryPathTree tree = new DirectoryPathTree(
                root,
                PathFilter.forIncludes(List.of("visible.txt")),
                true);

        final DirectoryPathTree copy = serializeAndDeserialize(tree);

        assertEquals(tree, copy);
        assertEquals(tree.hashCode(), copy.hashCode());
        assertFalse(copy.isArchiveOrigin());
        assertTrue(copy.isOpen());
        assertFalse(copy.isEmpty());
        assertEquals(List.of(root), List.copyOf(copy.getRoots()));
        assertTrue(copy.contains("visible.txt"));
        assertEquals(visibleFile, copy.getPath("visible.txt"));
        assertFalse(copy.contains("hidden.txt"));
        assertNull(copy.getPath("hidden.txt"));
        assertEquals(copy, copy.getOriginalTree());
    }

    private static DirectoryPathTree serializeAndDeserialize(final DirectoryPathTree tree)
            throws IOException, ClassNotFoundException {
        final byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(tree);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return assertInstanceOf(DirectoryPathTree.class, input.readObject());
        }
    }
}
