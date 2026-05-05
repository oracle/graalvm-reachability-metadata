/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import org.apache.ivy.util.Configurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfiguratorTest {

    @Test
    void configuresAttributesAndText() {
        Configurator configurator = new Configurator();
        RootBean root = new RootBean();

        configurator.setRoot(root);
        configurator.setAttribute("targetClass", TextReceiver.class.getName());
        configurator.setAttribute("customValue", "converted");
        configurator.setAttribute("enabled", "true");
        configurator.addText("body text");

        assertThat(root.getTargetClass()).isEqualTo(TextReceiver.class);
        assertThat(root.getCustomValue()).extracting(CustomValue::getValue).isEqualTo("converted");
        assertThat(root.isEnabled()).isTrue();
        assertThat(root.getText()).isEqualTo("body text");
    }

    @Test
    void createsNamedChildrenUsingFactoryAdderAndConfiguredAdder() {
        Configurator configurator = new Configurator();
        RootBean root = new RootBean();

        configurator.setRoot(root);
        Object created = configurator.startCreateChild("created");
        assertThat(created).isSameAs(root.getCreatedChild());
        assertThat(configurator.endCreateChild()).isSameAs(created);

        Object added = configurator.startCreateChild("added");
        assertThat(added).isSameAs(root.getAddedChild());
        assertThat(configurator.endCreateChild()).isSameAs(added);

        Object configured = configurator.startCreateChild("configured");
        assertThat(root.getNamedConfiguredChild()).isNull();
        assertThat(configurator.endCreateChild()).isSameAs(configured);
        assertThat(root.getNamedConfiguredChild()).isSameAs(configured);
    }

    @Test
    void createsTypedefChildrenUsingClassNameAndClassRegistration() throws ClassNotFoundException {
        Configurator configurator = new Configurator();
        RootBean root = new RootBean();

        configurator.typeDef("typedAdded", TypeAddedChild.class.getName());
        configurator.typeDef("typedConfigured", TypeConfiguredChild.class);
        configurator.setRoot(root);

        Object typedAdded = configurator.startCreateChild("typedAdded");
        assertThat(typedAdded).isSameAs(root.getTypeAddedChild());
        assertThat(configurator.endCreateChild()).isSameAs(typedAdded);

        Object typedConfigured = configurator.startCreateChild("typedConfigured");
        assertThat(root.getTypeConfiguredChild()).isNull();
        assertThat(configurator.endCreateChild()).isSameAs(typedConfigured);
        assertThat(root.getTypeConfiguredChild()).isSameAs(typedConfigured);
    }

    public static final class RootBean {
        private Class<?> targetClass;
        private CustomValue customValue;
        private boolean enabled;
        private String text;
        private CreatedChild createdChild;
        private AddedChild addedChild;
        private NamedConfiguredChild namedConfiguredChild;
        private TypeAddedChild typeAddedChild;
        private TypeConfiguredChild typeConfiguredChild;

        public Class<?> getTargetClass() {
            return targetClass;
        }

        public void setTargetClass(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        public CustomValue getCustomValue() {
            return customValue;
        }

        public void setCustomValue(CustomValue customValue) {
            this.customValue = customValue;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getText() {
            return text;
        }

        public void addText(String text) {
            this.text = text;
        }

        public CreatedChild getCreatedChild() {
            return createdChild;
        }

        public CreatedChild createCreated() {
            createdChild = new CreatedChild();
            return createdChild;
        }

        public AddedChild getAddedChild() {
            return addedChild;
        }

        public void addAdded(AddedChild addedChild) {
            this.addedChild = addedChild;
        }

        public NamedConfiguredChild getNamedConfiguredChild() {
            return namedConfiguredChild;
        }

        public void addConfiguredConfigured(NamedConfiguredChild namedConfiguredChild) {
            this.namedConfiguredChild = namedConfiguredChild;
        }

        public TypeAddedChild getTypeAddedChild() {
            return typeAddedChild;
        }

        public void add(TypeAddedChild typeAddedChild) {
            this.typeAddedChild = typeAddedChild;
        }

        public TypeConfiguredChild getTypeConfiguredChild() {
            return typeConfiguredChild;
        }

        public void addConfigured(TypeConfiguredChild typeConfiguredChild) {
            this.typeConfiguredChild = typeConfiguredChild;
        }
    }

    public static final class CustomValue {
        private final String value;

        public CustomValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class TextReceiver {
    }

    public static final class CreatedChild {
    }

    public static final class AddedChild {
    }

    public static final class NamedConfiguredChild {
    }

    public static final class TypeAddedChild {
    }

    public static final class TypeConfiguredChild {
    }
}
