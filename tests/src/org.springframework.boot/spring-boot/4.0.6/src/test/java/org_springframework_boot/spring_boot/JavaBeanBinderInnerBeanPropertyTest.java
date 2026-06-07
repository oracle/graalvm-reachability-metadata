/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanPropertyTest {

    @Test
    void bindExistingJavaBeanCollectionUsesGetterAndSetterMethods() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(
                Map.of("sample.items[0]", "bravo", "sample.items[1]", "charlie"));
        CollectionProperties properties = new CollectionProperties();

        CollectionProperties bound = new Binder(source).bind("sample", Bindable.ofInstance(properties)).get();

        assertThat(bound).isSameAs(properties);
        assertThat(properties.items).containsExactly("bravo", "charlie");
        assertThat(properties.getterCalls).isGreaterThanOrEqualTo(1);
        assertThat(properties.setterCalls).isEqualTo(1);
    }

    public static final class CollectionProperties {

        private List<String> items = new ArrayList<>(List.of("alpha"));

        private int getterCalls;

        private int setterCalls;

        public List<String> getItems() {
            this.getterCalls++;
            return this.items;
        }

        public void setItems(List<String> items) {
            this.setterCalls++;
            this.items = items;
        }

    }

}
