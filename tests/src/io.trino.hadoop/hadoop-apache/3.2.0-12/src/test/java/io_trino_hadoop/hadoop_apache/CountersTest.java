/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.CounterGroup;
import org.apache.hadoop.mapreduce.Counters;
import org.junit.jupiter.api.Test;

public class CountersTest {
    @Test
    void countersTrackAndMergeNamedCounterGroups() {
        Counters counters = new Counters();
        CounterGroup pipelineGroup = counters.addGroup("pipeline", "Pipeline metrics");
        Counter inputRecords = pipelineGroup.addCounter("inputRecords", "Input records", 0);
        Counter outputRecords = counters.findCounter("pipeline", "outputRecords");
        outputRecords.setDisplayName("Output records");

        inputRecords.increment(4);
        outputRecords.increment(3);
        Counters additionalCounters = new Counters();
        additionalCounters.findCounter("pipeline", "inputRecords").increment(2);
        additionalCounters.findCounter("pipeline", "skippedRecords").increment(1);

        counters.incrAllCounters(additionalCounters);

        CounterGroup mergedGroup = counters.getGroup("pipeline");
        assertThat(counters.getGroupNames()).containsExactly("pipeline");
        assertThat(counters.countCounters()).isEqualTo(3);
        assertThat(mergedGroup.getDisplayName()).isEqualTo("Pipeline metrics");
        assertThat(counters.findCounter("pipeline", "inputRecords").getValue()).isEqualTo(6);
        assertThat(counters.findCounter("pipeline", "outputRecords").getDisplayName())
                .isEqualTo("Output records");
        assertThat(counters.findCounter("pipeline", "outputRecords").getValue()).isEqualTo(3);
        assertThat(counters.findCounter("pipeline", "skippedRecords").getValue()).isEqualTo(1);
        assertThat(mergedGroup)
                .extracting(Counter::getName)
                .containsExactlyInAnyOrder("inputRecords", "outputRecords", "skippedRecords");
    }
}
