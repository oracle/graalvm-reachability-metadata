/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kerby.config.Conf;
import org.junit.jupiter.api.Test;

public class ConfTest {
    @Test
    void loadsNestedMapResources() {
        Conf conf = new Conf();
        Map<String, Object> childConfig = new LinkedHashMap<>();
        childConfig.put("child", "value");

        Map<String, Object> rootConfig = new LinkedHashMap<>();
        rootConfig.put("root", childConfig);

        conf.addMapConfig(rootConfig);

        assertThat(conf.getConfig("root")).isNotNull();
        assertThat(conf.getConfig("root").getString("child")).isEqualTo("value");
    }
}
