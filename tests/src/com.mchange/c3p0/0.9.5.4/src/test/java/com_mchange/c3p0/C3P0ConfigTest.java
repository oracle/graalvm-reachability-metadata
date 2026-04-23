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
import com.mchange.v2.c3p0.cfg.DefaultC3P0ConfigFinder;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void refreshMainConfigLoadsCustomConfigFinderFromConfiguredClassName() throws Exception {
        TrackingConfigFinder.reset();

        Properties overrides = new Properties();
        overrides.setProperty(C3P0Config.CFG_FINDER_CLASSNAME_KEY, TrackingConfigFinder.class.getName());
        MultiPropertiesConfig overrideConfig = MultiPropertiesConfig.fromProperties("C3P0ConfigTest", overrides);

        try {
            C3P0Config.refreshMainConfig(new MultiPropertiesConfig[] {overrideConfig}, "C3P0ConfigTest override");

            assertThat(TrackingConfigFinder.invocationCount()).isEqualTo(1);
            assertThat(C3P0Config.getMultiPropertiesConfig().getProperty(C3P0Config.CFG_FINDER_CLASSNAME_KEY))
                .isEqualTo(TrackingConfigFinder.class.getName());
            assertThat(C3P0Config.getUnspecifiedUserProperties(null)).isNotEmpty();
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }

    public static final class TrackingConfigFinder implements C3P0ConfigFinder {
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();

        public static int invocationCount() {
            return INVOCATIONS.get();
        }

        public static void reset() {
            INVOCATIONS.set(0);
        }

        @Override
        public C3P0Config findConfig() throws Exception {
            INVOCATIONS.incrementAndGet();
            return new DefaultC3P0ConfigFinder().findConfig();
        }
    }
}
