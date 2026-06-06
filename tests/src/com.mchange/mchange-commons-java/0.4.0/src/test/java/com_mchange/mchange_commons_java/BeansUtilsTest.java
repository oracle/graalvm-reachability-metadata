/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.BeansUtils;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BeansUtilsTest {
    @Test
    void findPropertyEditorInstantiatesConfiguredEditorClass() throws IntrospectionException {
        PropertyDescriptor propertyDescriptor = new PropertyDescriptor("name", MutableBean.class);
        propertyDescriptor.setPropertyEditorClass(TestStringEditor.class);

        PropertyEditor propertyEditor = BeansUtils.findPropertyEditor(propertyDescriptor);

        assertThat(propertyEditor).isInstanceOf(TestStringEditor.class);
    }

    @Test
    void extractAccessiblePropertiesToMapReadsBeanProperties() throws IntrospectionException {
        MutableBean bean = new MutableBean();
        bean.setName("alpha");
        bean.setCount(3);
        Map<String, Object> properties = new HashMap<>();

        BeansUtils.extractAccessiblePropertiesToMap(properties, bean);

        assertThat(properties)
                .containsEntry("count", 3)
                .containsEntry("name", "alpha");
    }

    @Test
    void overwriteAccessiblePropertiesCopiesBeanValues() throws IntrospectionException {
        MutableBean source = new MutableBean();
        source.setName("alpha");
        source.setCount(7);
        MutableBean destination = new MutableBean();
        destination.setName("before");
        destination.setCount(1);

        BeansUtils.overwriteAccessibleProperties(source, destination);

        assertThat(destination.getName()).isEqualTo("alpha");
        assertThat(destination.getCount()).isEqualTo(7);
    }

    @Test
    void overwriteAccessiblePropertiesFromMapAppliesDirectAndCoercedValues() throws IntrospectionException {
        MutableBean destination = new MutableBean();
        Map<String, Object> source = new HashMap<>();
        source.put("count", "9");
        source.put("name", "bravo");

        BeansUtils.overwriteAccessiblePropertiesFromMap(source, destination, false, List.of(), true, null, null, true);

        assertThat(destination.getName()).isEqualTo("bravo");
        assertThat(destination.getCount()).isEqualTo(9);
    }

    @Test
    void overwriteSpecificAccessiblePropertiesUsesSetterFromCompatibleTargetType() throws IntrospectionException {
        MutableBean source = new MutableBean();
        source.setName("charlie");
        CompatibleTargetBean destination = new CompatibleTargetBean();

        BeansUtils.overwriteSpecificAccessibleProperties(source, destination, List.of("name"));

        assertThat(destination.getName()).isEqualTo("charlie");
    }

    @Test
    void overwriteSpecificAccessiblePropertiesFindsMatchingSetterOnDifferentTargetType() throws IntrospectionException {
        MutableBean source = new MutableBean();
        source.setName("delta");
        AlternateBean destination = new AlternateBean();

        BeansUtils.overwriteSpecificAccessibleProperties(source, destination, List.of("name"));

        assertThat(destination.getName()).isEqualTo("delta");
    }

    public static class TestStringEditor extends PropertyEditorSupport {
        public TestStringEditor() {
        }
    }

    public static class MutableBean {
        private Integer count;
        private String name;

        public MutableBean() {
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class CompatibleTargetBean extends MutableBean {
        public CompatibleTargetBean() {
        }
    }

    public static class AlternateBean {
        private String name;

        public AlternateBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
