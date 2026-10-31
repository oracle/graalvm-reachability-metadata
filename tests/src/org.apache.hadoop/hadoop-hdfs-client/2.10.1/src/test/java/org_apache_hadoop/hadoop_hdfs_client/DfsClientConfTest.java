/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.client.HdfsClientConfigKeys;
import org.apache.hadoop.hdfs.client.impl.DfsClientConf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class DfsClientConfTest {
    @Test
    public void loadsConfiguredReplicaAccessorImplementation() {
        Configuration conf = new Configuration(false);
        conf.setStrings(HdfsClientConfigKeys.REPLICA_ACCESSOR_BUILDER_CLASSES_KEY,
                FileReplicaAccessorBuilder.class.getName());
        DfsClientConf clientConf = new DfsClientConf(conf);
        assertEquals(List.of(FileReplicaAccessorBuilder.class), clientConf.getReplicaAccessorBuilderClasses());
    }
}
