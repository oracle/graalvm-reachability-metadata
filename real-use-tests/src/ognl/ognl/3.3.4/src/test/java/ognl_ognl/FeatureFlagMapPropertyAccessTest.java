/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's map-property access subsystem for a feature-flag administration page.
 *
 * An operations application reads feature settings from a map-backed configuration and updates a
 * rollout value through the same expressions used by its form bindings.
 */
public class FeatureFlagMapPropertyAccessTest {
    @Test
    @Timeout(60)
    void readsAndUpdatesMapBackedFeatureConfiguration() throws OgnlException {
        FeatureConfiguration configuration = FeatureConfiguration.sample();
        Map context = Ognl.createDefaultContext(configuration);

        Object checkoutEnabled = Ognl.getValue(
                "settings['checkout.enabled']", context, configuration);
        Object rolloutPercent = Ognl.getValue(
                "settings['checkout.rolloutPercent']", context, configuration);
        Ognl.setValue("settings['checkout.rolloutPercent']", context, configuration, 75);
        Object updatedRolloutPercent = Ognl.getValue(
                "settings['checkout.rolloutPercent']", context, configuration);

        assertThat(checkoutEnabled).isEqualTo(true);
        assertThat(rolloutPercent).isEqualTo(25);
        assertThat(updatedRolloutPercent).isEqualTo(75);
        assertThat(configuration.getSettings())
                .containsEntry("checkout.rolloutPercent", 75);
    }

    public static final class FeatureConfiguration {
        private final Map<String, Object> settings;

        private FeatureConfiguration(Map<String, Object> settings) {
            this.settings = settings;
        }

        static FeatureConfiguration sample() {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("checkout.enabled", true);
            settings.put("checkout.rolloutPercent", 25);
            return new FeatureConfiguration(settings);
        }

        public Map<String, Object> getSettings() {
            return settings;
        }
    }
}
