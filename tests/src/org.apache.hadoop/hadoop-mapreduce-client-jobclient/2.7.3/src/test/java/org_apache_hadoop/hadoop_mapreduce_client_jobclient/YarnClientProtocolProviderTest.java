/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_jobclient;

import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.YarnClientProtocolProvider;
import org.apache.hadoop.mapreduce.protocol.ClientProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class YarnClientProtocolProviderTest {
    @Test
    void directProtocolCreationReturnsNullForLocalFramework() throws Exception {
        YarnClientProtocolProvider provider = new YarnClientProtocolProvider();
        Configuration configuration = localMapReduceConfiguration();

        ClientProtocol protocol = provider.create(configuration);

        assertThat(protocol).isNull();
        provider.close(protocol);
    }

    @Test
    void addressedProtocolCreationReturnsNullForLocalFramework() throws Exception {
        YarnClientProtocolProvider provider = new YarnClientProtocolProvider();
        Configuration configuration = localMapReduceConfiguration();
        InetSocketAddress address = InetSocketAddress.createUnresolved("localhost", 8032);

        ClientProtocol protocol = provider.create(address, configuration);

        assertThat(protocol).isNull();
        provider.close(protocol);
    }

    private static Configuration localMapReduceConfiguration() {
        Configuration configuration = new Configuration(false);
        configuration.set("mapreduce.framework.name", "local");
        return configuration;
    }
}
