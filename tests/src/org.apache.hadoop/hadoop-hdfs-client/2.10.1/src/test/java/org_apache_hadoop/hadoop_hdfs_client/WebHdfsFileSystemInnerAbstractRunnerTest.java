/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.web.WebHdfsFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class WebHdfsFileSystemInnerAbstractRunnerTest {
    @Test
    public void includesNamenodeAddressInHttpServiceErrors() throws Exception {
        try (TokenHttpServer server = new TokenHttpServer("503 Service Unavailable", "Maintenance");
                WebHdfsFileSystem fs = new WebHdfsFileSystem()) {
            Configuration conf = new Configuration(false);
            conf.setBoolean("dfs.http.client.retry.policy.enabled", false);
            conf.setInt("dfs.webhdfs.socket.connect-timeout", 10000);
            conf.setInt("dfs.webhdfs.socket.read-timeout", 10000);
            URI namenode = server.uri("webhdfs");
            fs.initialize(namenode, conf);
            IOException error = assertThrows(IOException.class,
                    () -> fs.getFileStatus(new Path("/maintenance-report")));
            assertEquals(IOException.class, error.getClass());
            assertTrue(error.getMessage().startsWith(namenode.getAuthority() + ": "), error::getMessage);
            assertTrue(error.getMessage().contains("code=503"), error::getMessage);
            assertTrue(error.getMessage().contains("Service Unavailable"), error::getMessage);
            assertTrue(server.request.get().startsWith("GET /webhdfs/v1/maintenance-report?"));
            assertTrue(server.request.get().contains("op=GETFILESTATUS"));
        }
    }
}
