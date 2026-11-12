/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.replacer.spi.ConfigReplacer;

import java.util.Properties;

public class ClusterNameReplacer implements ConfigReplacer {
    private Properties properties;

    @Override
    public void init(Properties properties) {
        this.properties = properties;
    }

    @Override
    public String getPrefix() {
        return "CLUSTER";
    }

    @Override
    public String getReplacement(String maskedValue) {
        return properties.getProperty(maskedValue);
    }
}
