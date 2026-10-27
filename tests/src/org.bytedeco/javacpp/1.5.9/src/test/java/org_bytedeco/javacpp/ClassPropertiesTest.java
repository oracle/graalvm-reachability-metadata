/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.ClassProperties;
import org.bytedeco.javacpp.LoadEnabled;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.annotation.Platform;

public class ClassPropertiesTest {

    @Platform(define = "JAVACPP_CLASS_PROPERTIES_TEST")
    public static class ConfigurableBinding implements LoadEnabled {
        @Override
        public void init(ClassProperties properties) {
            properties.setProperty("test.binding.initialized", "true");
        }
    }

    @Test
    public void loadInstantiatesAndAppliesLoadEnabledBinding() {
        ClassProperties properties = new ClassProperties(Loader.loadProperties());

        properties.load(ConfigurableBinding.class, false);

        assertThat(properties.isLoaded()).isTrue();
        assertThat(properties.get("platform.define"))
                .contains("JAVACPP_CLASS_PROPERTIES_TEST");
        assertThat(properties.getProperty("test.binding.initialized")).isEqualTo("true");
    }
}
