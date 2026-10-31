/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.FileNotFoundException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationUtilsClient;
import org.apache.hadoop.hdfs.web.URLConnectionFactory;
import org.apache.hadoop.security.token.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DelegationUtilsClientTest {
    @Test
    public void restoresServerExceptionWhenCancellingUnknownToken() throws Exception {
        try (TokenHttpServer server = new TokenHttpServer(
                "404 java.io.FileNotFoundException;Unknown delegation token", "")) {
            URLConnectionFactory connections = URLConnectionFactory.newDefaultURLConnectionFactory(
                    10000, 10000, new Configuration(false));
            FileNotFoundException error = assertThrows(FileNotFoundException.class,
                    () -> DelegationUtilsClient.cancelDelegationToken(connections,
                            server.uri("http"), new Token<DelegationTokenIdentifier>()));
            assertEquals("Unknown delegation token", error.getMessage());
            assertTrue(server.request.get().startsWith("GET /cancelDelegationToken?token="));
        }
    }
}
