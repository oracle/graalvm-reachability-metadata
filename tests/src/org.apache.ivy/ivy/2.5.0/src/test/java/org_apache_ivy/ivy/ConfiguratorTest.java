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
    void typedefFromClassNameCreatesAndAddsChildThroughGenericAdd() throws Exception {
        Configurator configurator = new Configurator();
        GenericAddRoot root = new GenericAddRoot();

        configurator.setRoot(root);
        configurator.typeDef("typed", TypedAddedChild.class.getName());
        TypedAddedChild child = (TypedAddedChild) configurator.startCreateChild("typed");
        Object completedChild = configurator.endCreateChild();

        assertThat(completedChild).isSameAs(child);
        assertThat(root.getTypedAddedChildren()).containsExactly(child);
    }

    @Test
    void typedefWithAddConfiguredDefersParentMutationUntilEndCreateChild() {
        Configurator configurator = new Configurator();
        GenericConfiguredRoot root = new GenericConfiguredRoot();

        configurator.setRoot(root);
        configurator.typeDef("configuredTyped", TypedConfiguredChild.class);
        TypedConfiguredChild child = (TypedConfiguredChild) configurator
                .startCreateChild("configuredTyped");

        assertThat(root.getTypedConfiguredChildren()).isEmpty();
        Object completedChild = configurator.endCreateChild();

        assertThat(completedChild).isSameAs(child);
        assertThat(root.getTypedConfiguredChildren()).containsExactly(child);
    }

    @Test
    void namedChildCreationUsesCreateAddAndAddConfiguredMethods() {
        Configurator configurator = new Configurator();
        NamedChildRoot root = new NamedChildRoot();

        configurator.setRoot(root);
        CreatedChild createdChild = (CreatedChild) configurator.startCreateChild("created");
        configurator.addText("created text");
        assertThat(configurator.endCreateChild()).isSameAs(createdChild);

        NamedAddedChild namedAddedChild = (NamedAddedChild) configurator.startCreateChild("named");
        assertThat(configurator.endCreateChild()).isSameAs(namedAddedChild);

        DeferredConfiguredChild deferredChild = (DeferredConfiguredChild) configurator
                .startCreateChild("deferred");
        assertThat(root.getDeferredConfiguredChildren()).isEmpty();
        assertThat(configurator.endCreateChild()).isSameAs(deferredChild);

        assertThat(createdChild.getText()).isEqualTo("created text");
        assertThat(root.getCreatedChildren()).containsExactly(createdChild);
        assertThat(root.getNamedAddedChildren()).containsExactly(namedAddedChild);
        assertThat(root.getDeferredConfiguredChildren()).containsExactly(deferredChild);
    }

    @Test
    void attributesConvertClassNamesAndStringConstructedValues() {
        Configurator configurator = new Configurator();
        AttributeRoot root = new AttributeRoot();

        configurator.setRoot(root);
        configurator.setAttribute("pluginType", String.class.getName());
        configurator.setAttribute("coordinate", "group:name");

        assertThat(root.getPluginType()).isEqualTo(String.class);
        assertThat(root.getCoordinate().getValue()).isEqualTo("group:name");
    }

    public static class GenericAddRoot {
        private final List<TypedAddedChild> typedAddedChildren = new ArrayList<>();

        public void add(TypedAddedChild child) {
            typedAddedChildren.add(child);
        }

        public List<TypedAddedChild> getTypedAddedChildren() {
            return typedAddedChildren;
        }
    }

    public static class GenericConfiguredRoot {
        private final List<TypedConfiguredChild> typedConfiguredChildren = new ArrayList<>();

        public void addConfigured(TypedConfiguredChild child) {
            typedConfiguredChildren.add(child);
        }

        public List<TypedConfiguredChild> getTypedConfiguredChildren() {
            return typedConfiguredChildren;
        }
    }

    public static class NamedChildRoot {
        private final List<CreatedChild> createdChildren = new ArrayList<>();

        private final List<NamedAddedChild> namedAddedChildren = new ArrayList<>();

        private final List<DeferredConfiguredChild> deferredConfiguredChildren = new ArrayList<>();

        public CreatedChild createCreated() {
            CreatedChild child = new CreatedChild();
            createdChildren.add(child);
            return child;
        }

        public void addNamed(NamedAddedChild child) {
            namedAddedChildren.add(child);
        }

        public void addConfiguredDeferred(DeferredConfiguredChild child) {
            deferredConfiguredChildren.add(child);
        }

        public List<CreatedChild> getCreatedChildren() {
            return createdChildren;
        }

        public List<NamedAddedChild> getNamedAddedChildren() {
            return namedAddedChildren;
        }

        public List<DeferredConfiguredChild> getDeferredConfiguredChildren() {
            return deferredConfiguredChildren;
        }
    }

    public static class AttributeRoot {
        private Class<?> pluginType;

        private Coordinate coordinate;

        public void setPluginType(Class<?> pluginType) {
            this.pluginType = pluginType;
        }

        public void setCoordinate(Coordinate coordinate) {
            this.coordinate = coordinate;
        }

        public Class<?> getPluginType() {
            return pluginType;
        }

        public Coordinate getCoordinate() {
            return coordinate;
        }
    }

    public static class TypedAddedChild {
    }

    public static class TypedConfiguredChild {
    }

    public static class CreatedChild {
        private String text;

        public void addText(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class NamedAddedChild {
    }

    public static class DeferredConfiguredChild {
    }

    public static class Coordinate {
        private final String value;

        public Coordinate(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
