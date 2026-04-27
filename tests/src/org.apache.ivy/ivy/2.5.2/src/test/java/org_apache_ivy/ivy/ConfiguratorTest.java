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
    public void configuresAttributesTextAndChildrenThroughPublicConfiguratorApi() throws Exception {
        AttributeTarget attributeTarget = new AttributeTarget();
        Configurator attributeConfigurator = new Configurator();
        attributeConfigurator.setRoot(attributeTarget);

        attributeConfigurator.setAttribute("targetClass", String.class.getName());
        attributeConfigurator.setAttribute("customValue", "converted-value");

        assertThat(attributeTarget.getTargetClass()).isEqualTo(String.class);
        assertThat(attributeTarget.getCustomValue().getValue()).isEqualTo("converted-value");

        TextTarget textTarget = new TextTarget();
        Configurator textConfigurator = new Configurator();
        textConfigurator.setRoot(textTarget);

        textConfigurator.addText("configured text");

        assertThat(textTarget.getText()).isEqualTo("configured text");

        ParentElement parentElement = new ParentElement();
        Configurator childConfigurator = new Configurator();
        childConfigurator.typeDef("typed", TypedAddChild.class.getName());
        childConfigurator.typeDef("configuredTyped", TypedConfiguredChild.class);
        childConfigurator.setRoot(parentElement);

        childConfigurator.startCreateChild("created");
        CreatedChild createdChild = (CreatedChild) childConfigurator.getCurrent();
        createdChild.setName("created-child");
        childConfigurator.endCreateChild();

        childConfigurator.startCreateChild("immediate");
        ImmediateChild immediateChild = (ImmediateChild) childConfigurator.getCurrent();
        immediateChild.setName("immediate-child");
        childConfigurator.endCreateChild();

        childConfigurator.startCreateChild("deferred");
        DeferredChild deferredChild = (DeferredChild) childConfigurator.getCurrent();
        deferredChild.setName("deferred-child");
        childConfigurator.endCreateChild();

        childConfigurator.startCreateChild("typed");
        TypedAddChild typedAddChild = (TypedAddChild) childConfigurator.getCurrent();
        typedAddChild.setName("typed-add-child");
        childConfigurator.endCreateChild();

        childConfigurator.startCreateChild("configuredTyped");
        TypedConfiguredChild typedConfiguredChild =
                (TypedConfiguredChild) childConfigurator.getCurrent();
        typedConfiguredChild.setName("typed-configured-child");
        childConfigurator.endCreateChild();

        assertThat(parentElement.getCreatedChild().getName()).isEqualTo("created-child");
        assertThat(parentElement.getImmediateChildren()).extracting(BaseChild::getName)
                .containsExactly("immediate-child", "typed-add-child");
        assertThat(parentElement.getDeferredChildren()).extracting(BaseChild::getName)
                .containsExactly("deferred-child", "typed-configured-child");
    }

    public static class AttributeTarget {
        private Class<?> targetClass;
        private CustomValue customValue;

        public void setTargetClass(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        public Class<?> getTargetClass() {
            return targetClass;
        }

        public void setCustomValue(CustomValue customValue) {
            this.customValue = customValue;
        }

        public CustomValue getCustomValue() {
            return customValue;
        }
    }

    public static class CustomValue {
        private final String value;

        public CustomValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class TextTarget {
        private String text;

        public void addText(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class ParentElement {
        private CreatedChild createdChild;
        private final List<BaseChild> immediateChildren = new ArrayList<>();
        private final List<BaseChild> deferredChildren = new ArrayList<>();

        public CreatedChild createCreated() {
            createdChild = new CreatedChild();
            return createdChild;
        }

        public void addImmediate(ImmediateChild child) {
            immediateChildren.add(child);
        }

        public void add(TypedAddChild child) {
            immediateChildren.add(child);
        }

        public void addConfiguredDeferred(DeferredChild child) {
            deferredChildren.add(child);
        }

        public void addConfigured(TypedConfiguredChild child) {
            deferredChildren.add(child);
        }

        public CreatedChild getCreatedChild() {
            return createdChild;
        }

        public List<BaseChild> getImmediateChildren() {
            return immediateChildren;
        }

        public List<BaseChild> getDeferredChildren() {
            return deferredChildren;
        }
    }

    public abstract static class BaseChild {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class CreatedChild extends BaseChild {
    }

    public static class ImmediateChild extends BaseChild {
    }

    public static class DeferredChild extends BaseChild {
    }

    public static class TypedAddChild extends BaseChild {
    }

    public static class TypedConfiguredChild extends BaseChild {
    }
}
