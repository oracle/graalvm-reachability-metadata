/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.BlockReader;
import org.apache.hadoop.hdfs.client.HdfsClientConfigKeys;
import org.apache.hadoop.hdfs.client.impl.BlockReaderFactory;
import org.apache.hadoop.hdfs.client.impl.DfsClientConf;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.security.token.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class BlockReaderFactoryTest {
    @TempDir
    public Path directory;

    @Test
    public void readsLocalReplicaThroughConfiguredAccessor() throws Exception {
        byte[] content = "prefix-payload-tail".getBytes(StandardCharsets.UTF_8);
        Path replica = directory.resolve("replica.data");
        Files.write(replica, content);
        Configuration conf = new Configuration(false);
        conf.setStrings(HdfsClientConfigKeys.REPLICA_ACCESSOR_BUILDER_CLASSES_KEY,
                FileReplicaAccessorBuilder.class.getName());
        BlockReader reader = new BlockReaderFactory(new DfsClientConf(conf))
                .setConfiguration(conf)
                .setFileName(replica.toString())
                .setBlock(new ExtendedBlock("pool", 42, content.length, 7))
                .setBlockToken(new Token<BlockTokenIdentifier>())
                .setClientName("replica-client")
                .setStartOffset(7)
                .setLength(7)
                .setVerifyChecksum(false)
                .setAllowShortCircuitLocalReads(true)
                .build();
        try {
            assertTrue(reader.isShortCircuit());
            assertEquals(0, reader.getNetworkDistance());
            assertEquals(7, reader.available());
            byte[] first = new byte[3];
            reader.readFully(first, 0, first.length);
            assertArrayEquals("pay".getBytes(StandardCharsets.UTF_8), first);
            ByteBuffer rest = ByteBuffer.allocate(4);
            assertEquals(4, reader.read(rest));
            assertArrayEquals("load".getBytes(StandardCharsets.UTF_8), rest.array());
            assertEquals(0, reader.available());
            assertEquals(-1, reader.read(new byte[1], 0, 1));
        } finally {
            reader.close();
        }
    }
}
