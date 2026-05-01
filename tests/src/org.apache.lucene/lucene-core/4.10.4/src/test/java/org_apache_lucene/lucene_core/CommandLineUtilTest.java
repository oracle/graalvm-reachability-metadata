/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.CommandLineUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CommandLineUtilTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void loadsDirectoryImplementationsFromCommandLineNames() throws ClassNotFoundException {
        Class<? extends Directory> directoryClass = CommandLineUtil.loadDirectoryClass("SimpleFSDirectory");
        Class<? extends FSDirectory> fsDirectoryClass = CommandLineUtil.loadFSDirectoryClass(
                SimpleFSDirectory.class.getName());

        assertThat(directoryClass).isEqualTo(SimpleFSDirectory.class);
        assertThat(fsDirectoryClass).isEqualTo(SimpleFSDirectory.class);
    }

    @Test
    public void createsFileSystemDirectoryFromCommandLineName()
            throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException,
            IllegalAccessException {
        File indexPath = temporaryDirectory.toFile();

        try (FSDirectory directory = CommandLineUtil.newFSDirectory(SimpleFSDirectory.class, indexPath);
                FSDirectory namedDirectory = CommandLineUtil.newFSDirectory("SimpleFSDirectory", indexPath)) {
            assertThat(directory).isInstanceOf(SimpleFSDirectory.class);
            assertThat(directory.getDirectory()).isEqualTo(indexPath.getCanonicalFile());
            assertThat(namedDirectory).isInstanceOf(SimpleFSDirectory.class);
            assertThat(namedDirectory.getDirectory()).isEqualTo(indexPath.getCanonicalFile());
        }
    }
}
