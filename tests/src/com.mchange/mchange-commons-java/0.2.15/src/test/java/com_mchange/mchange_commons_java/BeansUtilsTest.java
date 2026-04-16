/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.log.MLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeansUtilsTest {
    @Test
    void findPropertyEditorInstantiatesConfiguredEditorClass() throws Exception {
        PropertyDescriptor propertyDescriptor = new PropertyDescriptor("name", EditorBackedBean.class);
        propertyDescriptor.setPropertyEditorClass(NameEditor.class);

        PropertyEditor propertyEditor = BeansUtils.findPropertyEditor(propertyDescriptor);

        assertThat(propertyEditor).isInstanceOf(NameEditor.class);
    }

    @Test
    void extractAccessiblePropertiesToMapReadsBeanProperties() throws Exception {
        MutableBean bean = new MutableBean();
        bean.setCount(7);
        bean.setName("alpha");

        Map<String, Object> extracted = new LinkedHashMap<>();
        BeansUtils.extractAccessiblePropertiesToMap(extracted, bean);

        assertThat(extracted)
            .containsEntry("count", 7)
            .containsEntry("name", "alpha");
    }

    @Test
    void overwriteAccessiblePropertiesCopiesReadableWritableProperties() throws Exception {
        MutableBean source = new MutableBean();
        source.setCount(11);
        source.setName("beta");

        MutableBean destination = new MutableBean();
        destination.setCount(1);
        destination.setName("before");

        BeansUtils.overwriteAccessibleProperties(source, destination);

        assertThat(destination.getCount()).isEqualTo(11);
        assertThat(destination.getName()).isEqualTo("beta");
    }

    @Test
    void overwriteAccessiblePropertiesFromMapCoercesStringValuesAndWritesDirectValues() throws Exception {
        MutableBean destination = new MutableBean();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("count", "42");
        source.put("name", "gamma");

        BeansUtils.overwriteAccessiblePropertiesFromMap(
            source,
            destination,
            false,
            Set.of(),
            true,
            MLevel.WARNING,
            MLevel.WARNING,
            true
        );

        assertThat(destination.getCount()).isEqualTo(42);
        assertThat(destination.getName()).isEqualTo("gamma");
    }

    @Test
    void overwriteSpecificAccessiblePropertiesUsesSetterFromSharedBeanType() throws Exception {
        MutableBean source = new MutableBean();
        source.setCount(99);
        source.setName("delta");

        MutableBean destination = new MutableBean();
        destination.setCount(5);
        destination.setName("before");

        BeansUtils.overwriteSpecificAccessibleProperties(source, destination, Set.of("name"));

        assertThat(destination.getName()).isEqualTo("delta");
        assertThat(destination.getCount()).isEqualTo(5);
    }

    @Test
    void overwriteSpecificAccessiblePropertiesFindsMatchingSetterOnDifferentBeanType() throws Exception {
        MutableBean source = new MutableBean();
        source.setName("epsilon");

        AlternateNameBean destination = new AlternateNameBean();
        destination.setName("before");

        BeansUtils.overwriteSpecificAccessibleProperties(source, destination, Set.of("name"));

        assertThat(destination.getName()).isEqualTo("epsilon");
    }

    public static final class EditorBackedBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class NameEditor extends PropertyEditorSupport {
    }

    public static final class MutableBean {
        private int count;
        private String name;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class AlternateNameBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
