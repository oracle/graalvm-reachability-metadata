/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.AbstractConfig;
import com.alibaba.dubbo.config.support.Parameter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AbstractConfigTest {
    @Test
    void mapsAnnotationsPropertiesParametersAndAttributes() {
        ProbeConfig config = new ProbeConfig();
        Class<Annotated> annotatedAnnotationAccess = Annotated.class;
        config.apply(annotatedAnnotationAccess.getAnnotation(Setting.class));
        assertThat(config.getValue()).isEqualTo("annotation");

        System.setProperty("dubbo.probe.value", "property");
        try {
            config.setValue(null);
            config.loadProperties();
            assertThat(config.getValue()).isEqualTo("property");
        } finally {
            System.clearProperty("dubbo.probe.value");
        }

        config.setEnabled(Boolean.TRUE);
        config.loadProperties();
        assertThat(config.isEnabled()).isTrue();

        config.setValue("configured");
        config.getParameters().put("custom-key", "custom-value");
        assertThat(config.parameters())
                .containsEntry("value", "configured")
                .containsEntry("custom.key", "custom-value");
        assertThat(config.attributes()).containsEntry("value", "configured");
        assertThat(config.toString()).contains("value=\"configured\"");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Setting {
        String value() default "default";
    }

    @Setting("annotation")
    public static class Annotated {}

    public static class ProbeConfig extends AbstractConfig {
        private String value;
        private Boolean enabled;
        private final Map<String, String> parameters = new HashMap<String, String>();

        @Parameter(attribute = true)
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        void apply(Setting setting) {
            appendAnnotation(Setting.class, setting);
        }

        void loadProperties() {
            appendProperties(this);
        }

        Map<String, String> parameters() {
            Map<String, String> result = new HashMap<String, String>();
            appendParameters(result, this);
            return result;
        }

        Map<Object, Object> attributes() {
            Map<Object, Object> result = new HashMap<Object, Object>();
            appendAttributes(result, this);
            return result;
        }
    }
}
