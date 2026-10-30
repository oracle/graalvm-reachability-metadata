/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio;

import groovy.lang.Writable;
import org.apache.groovy.nio.extensions.NioExtensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
public class WritablePathTest {
    @TempDir
    Path directory;

    @Test
    void writableViewPreservesFileIdentityAndPathOperations() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("documents"));
        Path file = Files.writeString(folder.resolve("note.txt"), "saved note");
        Path writable = NioExtensions.asWritable(file);

        assertThat(writable).isInstanceOf(Writable.class);
        assertThat(writable.getFileSystem()).isSameAs(file.getFileSystem());
        assertThat(writable.toRealPath()).isEqualTo(file.toRealPath());
        assertThat(writable.toUri()).isEqualTo(file.toUri());
        assertThat(writable.toFile()).hasContent("saved note");
        assertThat(writable.getFileName()).isEqualTo(Path.of("note.txt"));
        assertThat(writable.getParent()).isEqualTo(folder);
        assertThat(writable.resolveSibling("copy.txt")).isEqualTo(folder.resolve("copy.txt"));
        assertThat(writable.relativize(folder.resolve("other.txt")))
                .isEqualTo(Path.of("..", "other.txt"));
        assertThat(writable.compareTo(file)).isZero();
        assertThat(writable.hashCode()).isEqualTo(file.hashCode());
    }

    @Test
    void encodedWritableViewRetainsRelativePathStructure() {
        Path file = Path.of("documents", "drafts", "..", "note.txt");
        Path writable = NioExtensions.asWritable(file, "UTF-8");

        assertThat(writable.isAbsolute()).isFalse();
        assertThat(writable.normalize()).isEqualTo(Path.of("documents", "note.txt"));
        assertThat(writable.startsWith(Path.of("documents"))).isTrue();
        assertThat(writable.endsWith("note.txt")).isTrue();
        assertThat(writable.getNameCount()).isEqualTo(4);
        assertThat(writable.subpath(0, 2)).isEqualTo(Path.of("documents", "drafts"));
        assertThat(writable.resolve("child")).isEqualTo(file.resolve("child"));
        assertThat(writable.toAbsolutePath()).isEqualTo(file.toAbsolutePath());
        List<Path> components = new ArrayList<>();
        writable.forEach(components::add);
        assertThat(components).containsExactly(Path.of("documents"), Path.of("drafts"),
                Path.of(".."), Path.of("note.txt"));
    }
}
