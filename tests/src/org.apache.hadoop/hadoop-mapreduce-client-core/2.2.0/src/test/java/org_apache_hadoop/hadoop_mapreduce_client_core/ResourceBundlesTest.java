/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ResourceBundle;

import org.apache.hadoop.mapreduce.TaskCounter;
import org.apache.hadoop.mapreduce.util.ResourceBundles;
import org.junit.jupiter.api.Test;

public class ResourceBundlesTest {
    @Test
    void loadsMapReduceCounterResourceBundleWithContextClassLoader() {
        ResourceBundle bundle = ResourceBundles.getBundle(TaskCounter.class.getName());

        assertThat(bundle.getString("CounterGroupName")).isEqualTo("Map-Reduce Framework");
        assertThat(bundle.getString("MAP_INPUT_RECORDS.name")).isEqualTo("Map input records");
    }
}
