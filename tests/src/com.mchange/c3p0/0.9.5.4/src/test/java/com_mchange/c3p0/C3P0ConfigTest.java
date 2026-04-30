/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigFinder;
import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import java.util.HashMap;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class C3P0ConfigTest {
    private static final String CONFIG_FINDER_PROPERTY = "com.mchange.v2.c3p0.cfg.finder";

    @Test
    void refreshesMainConfigWithConfiguredConfigFinder() {
        Properties properties = new Properties();
        properties.setProperty(CONFIG_FINDER_PROPERTY, CountingConfigFinder.class.getName());
        MultiPropertiesConfig overrides = MultiPropertiesConfig.fromProperties(properties);
        CountingConfigFinder.reset();

        try {
            C3P0Config.refreshMainConfig(new MultiPropertiesConfig[] {overrides}, "test config finder");

            assertThat(CountingConfigFinder.findConfigInvocations()).isEqualTo(1);
            assertThat(C3P0Config.getUnspecifiedUserProperty("maxPoolSize", null)).isEqualTo("7");
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }

    @Test
    void bindsUserOverridesStringThroughBeanSetter() throws Exception {
        UserOverridesBean bean = new UserOverridesBean();

        C3P0Config.bindUserOverridesAsString(bean, "user-overrides-payload");

        assertThat(bean.getUserOverridesAsString()).isEqualTo("user-overrides-payload");
        assertThat(bean.getSetterInvocations()).isEqualTo(1);
    }

    public static final class CountingConfigFinder implements C3P0ConfigFinder {
        private static int findConfigInvocations;

        public CountingConfigFinder() {
        }

        @Override
        public C3P0Config findConfig() {
            findConfigInvocations++;
            HashMap<String, Object> defaults = new HashMap<>();
            defaults.put("maxPoolSize", "7");
            return C3P0ConfigUtils.configFromFlatDefaults(defaults);
        }

        private static void reset() {
            findConfigInvocations = 0;
        }

        private static int findConfigInvocations() {
            return findConfigInvocations;
        }
    }

    public static final class UserOverridesBean {
        private String userOverridesAsString;
        private int setterInvocations;

        public void setUserOverridesAsString(String userOverridesAsString) {
            this.userOverridesAsString = userOverridesAsString;
            setterInvocations++;
        }

        private String getUserOverridesAsString() {
            return userOverridesAsString;
        }

        private int getSetterInvocations() {
            return setterInvocations;
        }
    }
}
