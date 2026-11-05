/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigFinder;
import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class C3P0ConfigTest {
    @Test
    void applicationConfigFinderControlsDataSourceDefaults() throws Exception {
        try (AutoCloseable configuration = C3p0Support.configuration(
                        C3P0Config.CFG_FINDER_CLASSNAME_KEY, ApplicationConfigFinder.class.getName())) {
            WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource(false);
            assertEquals(6, source.getMaxPoolSize());
            assertEquals(2, source.getMinPoolSize());
        }
    }

    @Test
    void bindsUserOverridesToTheDataSourceBean() throws Exception {
        WrapperConnectionPoolDataSource source = C3p0Support.wrapper(false);
        HashMap<String, Object> userProperties = new HashMap<>(Map.of("maxPoolSize", 2, "preferredTestQuery", "SELECT 1"));
        HashMap<String, Object> overrides = new HashMap<>(Map.of("sa", userProperties));
        String encoded = C3P0ImplUtils.createUserOverridesAsString(overrides);
        C3P0Config.bindUserOverridesAsString(source, encoded);
        assertEquals(encoded, source.getUserOverridesAsString());
        assertEquals(userProperties, source.getUserOverrides().get("sa"));
    }

    public static class ApplicationConfigFinder implements C3P0ConfigFinder {
        @Override
        public C3P0Config findConfig() {
            HashMap<String, String> defaults = new HashMap<>();
            defaults.put("maxPoolSize", "6");
            defaults.put("minPoolSize", "2");
            return C3P0ConfigUtils.configFromFlatDefaults(defaults);
        }
    }
}
