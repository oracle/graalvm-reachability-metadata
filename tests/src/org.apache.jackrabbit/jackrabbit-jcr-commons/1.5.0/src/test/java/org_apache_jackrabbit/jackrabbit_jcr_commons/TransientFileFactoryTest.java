/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_jcr_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.jackrabbit.util.TransientFileFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TransientFileFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void createsTransientFileInRequestedDirectory() throws IOException {
        TransientFileFactory factory = TransientFileFactory.getInstance();

        File transientFile = factory.createTransientFile("jcr", ".tmp", temporaryDirectory.toFile());

        assertThat(transientFile).exists().isFile();
        assertThat(transientFile.getName()).startsWith("jcr").endsWith(".tmp");
        assertThat(transientFile.getParentFile()).isEqualTo(temporaryDirectory.toFile());
        assertThat(transientFile.delete()).isTrue();
    }
}
