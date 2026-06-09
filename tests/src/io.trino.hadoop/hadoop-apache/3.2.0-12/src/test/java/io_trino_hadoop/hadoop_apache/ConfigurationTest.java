/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.junit.jupiter.api.Test;

public class ConfigurationTest {
    @Test
    void typedPropertiesAreParsedFromProgrammaticConfiguration() {
        Configuration conf = new Configuration(false);
        conf.setBoolean("feature.enabled", true);
        conf.setInt("worker.count", 3);
        conf.setPattern("path.pattern", Pattern.compile("part-[0-9]+"));
        conf.setStrings("enabled.names", "alpha", "beta", "gamma");

        Collection<String> names = conf.getStringCollection("enabled.names");

        assertThat(conf.getBoolean("feature.enabled", false)).isTrue();
        assertThat(conf.getInt("worker.count", 0)).isEqualTo(3);
        assertThat(conf.getPattern("path.pattern", Pattern.compile("missing")).pattern())
                .isEqualTo("part-[0-9]+");
        assertThat(names).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void configuredClassIsInstantiatedAndReceivesConfiguration() {
        Configuration conf = new Configuration(false);
        conf.setClass("codec.impl", DefaultCodec.class, CompressionCodec.class);

        CompressionCodec codec = conf.getInstances("codec.impl", CompressionCodec.class).get(0);

        assertThat(codec).isInstanceOf(DefaultCodec.class);
        assertThat(((Configurable) codec).getConf()).isSameAs(conf);
    }
}
