/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.AbstractFileSystem;
import org.apache.hadoop.fs.local.LocalFs;
import org.junit.jupiter.api.Test;

public class AbstractFileSystemTest {
    @Test
    void getCreatesConfiguredLocalFileSystem() throws Exception {
        URI uri = URI.create("file:///");
        Configuration conf = new Configuration(false);
        conf.setClass("fs.AbstractFileSystem.file.impl", LocalFs.class, AbstractFileSystem.class);

        AbstractFileSystem fileSystem = AbstractFileSystem.get(uri, conf);

        assertThat(fileSystem).isInstanceOf(LocalFs.class);
        assertThat(fileSystem.getUri()).isEqualTo(uri);
        assertThat(fileSystem.getStatistics()).isNotNull();
    }
}
