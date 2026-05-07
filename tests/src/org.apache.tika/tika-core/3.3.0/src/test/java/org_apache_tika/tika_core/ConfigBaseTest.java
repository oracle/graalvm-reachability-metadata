/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;

public class ConfigBaseTest {

    @Test
    public void configureSetsPrimitiveComplexListAndMapParameters() throws Exception {
        ConfigurableConfig config = new ConfigurableConfig();
        String xml = """
                <properties>
                  <config>
                    <intValue>7</intValue>
                    <longValue>123456789</longValue>
                    <floatValue>1.5</floatValue>
                    <doubleValue>2.25</doubleValue>
                    <enabled>true</enabled>
                    <stringValue>configured</stringValue>
                    <alias>adder-value</alias>
                    <names>
                      <name>alpha</name>
                      <name>beta</name>
                    </names>
                    <mappings>
                      <entry from="first" to="one"/>
                      <entry k="second" v="two"/>
                    </mappings>
                    <nested class="%s">
                      <value>nested-value</value>
                    </nested>
                  </config>
                </properties>
                """.formatted(NestedConfig.class.getName());

        Set<String> settings = config.configureFrom(xml);

        assertThat(settings).contains("intValue", "longValue", "floatValue", "doubleValue",
                "enabled", "stringValue", "alias", "names", "mappings", "nested");
        assertThat(config.intValue).isEqualTo(7);
        assertThat(config.longValue).isEqualTo(123456789L);
        assertThat(config.floatValue).isEqualTo(1.5f);
        assertThat(config.doubleValue).isEqualTo(2.25d);
        assertThat(config.enabled).isTrue();
        assertThat(config.stringValue).isEqualTo("configured");
        assertThat(config.aliases).containsExactly("adder-value");
        assertThat(config.names).containsExactly("alpha", "beta");
        assertThat(config.mappings).containsEntry("first", "one").containsEntry("second", "two");
        assertThat(config.nested.value).isEqualTo("nested-value");
    }

    @Test
    public void buildCompositeCreatesCompositeWithConfiguredItems() throws Exception {
        String xml = """
                <properties>
                  <group>
                    <item class="%s">
                      <stringValue>first</stringValue>
                    </item>
                    <item class="%s">
                      <stringValue>second</stringValue>
                    </item>
                    <description>loaded-composite</description>
                  </group>
                </properties>
                """.formatted(SimpleItem.class.getName(), SimpleItem.class.getName());

        CompositeGroup group = ConfigurableConfig.buildCompositeFrom(xml);

        assertThat(group.items)
                .extracting(item -> item.stringValue)
                .containsExactly("first", "second");
        assertThat(group.description).isEqualTo("loaded-composite");
    }

    private static InputStream xmlStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static class ConfigurableConfig extends ConfigBase {
        private int intValue;
        private long longValue;
        private float floatValue;
        private double doubleValue;
        private boolean enabled;
        private String stringValue;
        private List<String> names;
        private Map<String, String> mappings;
        private NestedConfig nested;
        private final List<String> aliases = new ArrayList<>();

        public Set<String> configureFrom(String xml) throws TikaConfigException, IOException {
            return configure("config", xmlStream(xml));
        }

        public static CompositeGroup buildCompositeFrom(String xml)
                throws TikaConfigException, IOException {
            return buildComposite("group", CompositeGroup.class, "item", SimpleItem.class,
                    xmlStream(xml));
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public void addAlias(String alias) {
            aliases.add(alias);
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        public void setNested(NestedConfig nested) {
            this.nested = nested;
        }
    }

    public static class NestedConfig {
        private String value;

        public NestedConfig() {
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class SimpleItem {
        private String stringValue;

        public SimpleItem() {
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }

    public static class CompositeGroup {
        private final List<SimpleItem> items;
        private String description;

        public CompositeGroup(List<SimpleItem> items) {
            this.items = items;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
