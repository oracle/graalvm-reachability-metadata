/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_jobclient;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.ClientCache;
import org.apache.hadoop.mapred.ClientServiceDelegate;
import org.apache.hadoop.mapreduce.JobID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class Hadoop_mapreduce_client_jobclientTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }

    @Test
    void clientCacheReusesClientServiceDelegateForTheSameJob() throws Exception {
        ClientCache cache = new ClientCache(clientCacheConfiguration(), null);
        JobID firstJob = new JobID("123456789", 1);
        JobID secondJob = new JobID("123456789", 2);

        try {
            ClientServiceDelegate firstClient = cache.getClient(firstJob);
            ClientServiceDelegate sameJobClient = cache.getClient(firstJob);
            ClientServiceDelegate otherJobClient = cache.getClient(secondJob);

            assertThat(sameJobClient).isSameAs(firstClient);
            assertThat(otherJobClient).isNotSameAs(firstClient);
        } finally {
            cache.close();
        }
    }

    private static Configuration clientCacheConfiguration() {
        return new Configuration(false);
    }
}
