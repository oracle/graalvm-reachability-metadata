/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.CommandLineUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommandLineUtilTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsDirectoryImplementationFromSimpleAndFullyQualifiedNames() throws ClassNotFoundException {
        Class<? extends Directory> simpleNameClass = CommandLineUtil.loadDirectoryClass("RAMDirectory");
        Class<? extends Directory> fullyQualifiedClass =
                CommandLineUtil.loadDirectoryClass("org.apache.lucene.store.MMapDirectory");

        assertThat(simpleNameClass).isEqualTo(RAMDirectory.class);
        assertThat(fullyQualifiedClass).isEqualTo(MMapDirectory.class);
    }

    @Test
    void loadsFSDirectoryImplementationFromSimpleName() throws ClassNotFoundException {
        Class<? extends FSDirectory> fsDirectoryClass = CommandLineUtil.loadFSDirectoryClass("SimpleFSDirectory");

        assertThat(fsDirectoryClass).isEqualTo(SimpleFSDirectory.class);
    }

    @Test
    void createsFSDirectoryByClassNameUsingFileConstructor() throws IOException {
        File directoryPath = temporaryDirectory.toFile();

        FSDirectory directory = CommandLineUtil.newFSDirectory("NIOFSDirectory", directoryPath);
        try {
            assertThat(directory).isInstanceOf(NIOFSDirectory.class);
            assertThat(directory.getDirectory()).isEqualTo(directoryPath.getCanonicalFile());
        } finally {
            directory.close();
        }
    }

    @Test
    void reportsNonFSDirectoryClassNames() {
        assertThatThrownBy(() -> CommandLineUtil.loadFSDirectoryClass("RAMDirectory"))
                .isInstanceOf(ClassCastException.class);
        assertThatThrownBy(() -> CommandLineUtil.newFSDirectory("RAMDirectory", temporaryDirectory.toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RAMDirectory is not a FSDirectory implementation");
    }
}
