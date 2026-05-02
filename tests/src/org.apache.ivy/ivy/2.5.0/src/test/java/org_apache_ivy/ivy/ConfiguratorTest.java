/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.ivy.util.Configurator;
import org.junit.jupiter.api.Test;

public class ConfiguratorTest {

    @Test
    public void configuresRootAndChildrenThroughPublicConfiguratorApi() throws Exception {
        Configurator configurator = new Configurator();
        ConfigurableRoot root = new ConfigurableRoot();
        configurator.setRoot(root);

        configurator.typeDef("typedAddedChild", TypeAddedChild.class.getName());
        configurator.typeDef("typedConfiguredChild", TypeConfiguredChild.class);
        configurator.setAttribute("targetClass", CreatedChild.class.getName());
        configurator.setAttribute("textValue", "converted-value");
        configurator.addText("root-text");

        Object createdChild = configurator.startCreateChild("createdChild");
        configurator.addText("created-text");
        configurator.endCreateChild();

        Object startAddedChild = configurator.startCreateChild("startAddedChild");
        configurator.endCreateChild();

        Object configuredChild = configurator.startCreateChild("configuredChild");
        configurator.endCreateChild();

        Object typedAddedChild = configurator.startCreateChild("typedAddedChild");
        configurator.endCreateChild();

        Object typedConfiguredChild = configurator.startCreateChild("typedConfiguredChild");
        configurator.endCreateChild();

        assertThat(root.targetClass).isEqualTo(CreatedChild.class);
        assertThat(root.textValue.value).isEqualTo("converted-value");
        assertThat(root.text).isEqualTo("root-text");
        assertThat(createdChild).isSameAs(root.createdChild);
        assertThat(root.createdChild.text).isEqualTo("created-text");
        assertThat(startAddedChild).isSameAs(root.startAddedChild);
        assertThat(configuredChild).isSameAs(root.configuredChild);
        assertThat(root.typedAddedChildren).containsExactly((TypeAddedChild) typedAddedChild);
        assertThat(root.typedConfiguredChild).isSameAs(typedConfiguredChild);
    }

    public static class ConfigurableRoot {
        private Class<?> targetClass;
        private TextValue textValue;
        private String text;
        private CreatedChild createdChild;
        private StartAddedChild startAddedChild;
        private ConfiguredChild configuredChild;
        private final List<TypeAddedChild> typedAddedChildren = new ArrayList<>();
        private TypeConfiguredChild typedConfiguredChild;

        public void setTargetClass(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        public void setTextValue(TextValue textValue) {
            this.textValue = textValue;
        }

        public void addText(String text) {
            this.text = text;
        }

        public CreatedChild createCreatedChild() {
            createdChild = new CreatedChild();
            return createdChild;
        }

        public void addStartAddedChild(StartAddedChild child) {
            startAddedChild = child;
        }

        public void addConfiguredConfiguredChild(ConfiguredChild child) {
            configuredChild = child;
        }

        public void add(TypeAddedChild child) {
            typedAddedChildren.add(child);
        }

        public void addConfigured(TypeConfiguredChild child) {
            typedConfiguredChild = child;
        }
    }

    public static class TextValue {
        private final String value;

        public TextValue(String value) {
            this.value = value;
        }
    }

    public static class CreatedChild {
        private String text;

        public void addText(String text) {
            this.text = text;
        }
    }

    public static class StartAddedChild {
    }

    public static class ConfiguredChild {
    }

    public static class TypeAddedChild {
    }

    public static class TypeConfiguredChild {
    }
}
