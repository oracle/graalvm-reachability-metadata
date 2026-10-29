/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_log4j2_adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import com.alibaba.nacos.logger.adapter.log4j2.Log4j2NacosLoggingPropertiesHolder;
import com.alibaba.nacos.logger.adapter.log4j2.NacosClientPropertiesLookup;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("nacos-log4j2-properties")
@Timeout(120)
public class NacosClientPropertiesLookupTest {
    @Test
    void resolvesConfiguredValuesWithAndWithoutLogEvents() {
        Properties values = new Properties();
        values.setProperty("JM.LOG.FILE.SIZE", "24MB");
        NacosLoggingProperties properties = new NacosLoggingProperties(null, values);
        NacosLoggingProperties previous = Log4j2NacosLoggingPropertiesHolder.getProperties();
        try {
            Log4j2NacosLoggingPropertiesHolder.setProperties(properties);
            NacosClientPropertiesLookup lookup = new NacosClientPropertiesLookup();

            assertThat(lookup.lookup("JM.LOG.FILE.SIZE")).isEqualTo("24MB");
            assertThat(lookup.lookup(Log4jLogEvent.newBuilder().build(), "JM.LOG.FILE.SIZE")).isEqualTo("24MB");
            assertThat(lookup.lookup("missing.nacos.property")).isNull();

            Properties replacement = new Properties();
            replacement.setProperty("JM.LOG.FILE.SIZE", "32MB");
            Log4j2NacosLoggingPropertiesHolder.setProperties(new NacosLoggingProperties(null, replacement));
            assertThat(lookup.lookup("JM.LOG.FILE.SIZE")).isEqualTo("32MB");
        } finally {
            Log4j2NacosLoggingPropertiesHolder.setProperties(previous);
        }
    }

    @Test
    void returnsNoValueWhenPropertiesHaveNotBeenInstalled() {
        NacosLoggingProperties previous = Log4j2NacosLoggingPropertiesHolder.getProperties();
        try {
            Log4j2NacosLoggingPropertiesHolder.setProperties(null);

            assertThat(new NacosClientPropertiesLookup().lookup("JM.LOG.FILE.SIZE")).isNull();
            assertThat(Log4j2NacosLoggingPropertiesHolder.getValue("JM.LOG.FILE.SIZE")).isNull();
        } finally {
            Log4j2NacosLoggingPropertiesHolder.setProperties(previous);
        }
    }
}
