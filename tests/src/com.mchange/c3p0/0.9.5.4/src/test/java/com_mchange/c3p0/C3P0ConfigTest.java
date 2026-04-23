/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0ConfigTest {
    @Test
    void bindsUserOverridesThroughPublicBeanApi() throws Exception {
        Map<String, Map<String, String>> overrides = new HashMap<>();
        Map<String, String> userOverrides = new HashMap<>();
        userOverrides.put("maxPoolSize", "7");
        overrides.put("sa", userOverrides);
        String serializedOverrides = C3P0ImplUtils.createUserOverridesAsString(overrides);

        WrapperConnectionPoolDataSource dataSource = new WrapperConnectionPoolDataSource();
        C3P0Config.bindUserOverridesAsString(dataSource, serializedOverrides);

        assertThat(dataSource.getUserOverridesAsString()).isEqualTo(serializedOverrides);
        assertThat(C3P0Config.getUnspecifiedUserProperties(null)).isNotEmpty();
    }
}
