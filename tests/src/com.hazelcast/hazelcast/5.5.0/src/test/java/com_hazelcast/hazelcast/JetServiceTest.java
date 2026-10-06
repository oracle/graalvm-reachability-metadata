/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class JetServiceTest {
    @Test
    void copiesDistributedMapWithBatchPipeline()
            throws ExecutionException, InterruptedException, TimeoutException {
        Config config = new Config();
        config.setClusterName("jet-pipeline-test");
        config.getNetworkConfig().setPort(0);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getJetConfig().setEnabled(true);
        config.addMapConfig(new MapConfig("jet-source").setBackupCount(0));
        config.addMapConfig(new MapConfig("jet-target").setBackupCount(0));

        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        try {
            IMap<String, Integer> source = instance.getMap("jet-source");
            IMap<String, Integer> target = instance.getMap("jet-target");
            source.putAll(Map.of("one", 1, "two", 2, "three", 3));

            Pipeline pipeline = Pipeline.create();
            pipeline.readFrom(Sources.map(source)).writeTo(Sinks.map(target));
            Job job = instance.getJet().newJob(pipeline);
            job.getFuture().get(30, TimeUnit.SECONDS);

            assertThat(target.entrySet()).containsExactlyInAnyOrderElementsOf(source.entrySet());
        } finally {
            instance.shutdown();
        }
    }
}
