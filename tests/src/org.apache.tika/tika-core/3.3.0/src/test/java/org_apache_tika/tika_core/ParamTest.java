/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.Param;

public class ParamTest {

    @Test
    public void loadCreatesConfiguredObjectFromClassParameter() throws Exception {
        String xml = """
                <param name="bean" type="class" class="%s">
                  <params>
                    <param name="text" type="string">configured text</param>
                    <param name="count" type="int">42</param>
                    <param name="customValue" type="%s">custom payload</param>
                  </params>
                </param>
                """.formatted(ConfiguredBean.class.getName(), StringConstructible.class.getName());

        Param<ConfiguredBean> param = Param.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(param.getName()).isEqualTo("bean");
        assertThat(param.getType()).isEqualTo(ConfiguredBean.class);
        assertThat(param.getValue().getText()).isEqualTo("configured text");
        assertThat(param.getValue().getCount()).isEqualTo(42);
        assertThat(param.getValue().getCustomValue().getValue()).isEqualTo("custom payload");
    }

    public static class ConfiguredBean {
        private String text;
        private Integer count;
        private StringConstructible customValue;

        public ConfiguredBean() {
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public void setCustomValue(StringConstructible customValue) {
            this.customValue = customValue;
        }

        public String getText() {
            return text;
        }

        public Integer getCount() {
            return count;
        }

        public StringConstructible getCustomValue() {
            return customValue;
        }
    }

    public static class StringConstructible {
        private final String value;

        public StringConstructible(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
