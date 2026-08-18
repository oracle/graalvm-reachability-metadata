/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

public class FieldUtilsTest {

    @Test
    public void getDeclaredFieldReturnsPrivateFieldWhenForceAccessIsEnabled() {
        Field field = FieldUtils.getDeclaredField(DeclaredFieldTarget.class, "hiddenValue", true);

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(DeclaredFieldTarget.class);
        assertThat(field.getName()).isEqualTo("hiddenValue");
    }

    @Test
    public void getFieldFindsInheritedPrivateFieldWhenForceAccessIsEnabled() {
        Field field = FieldUtils.getField(InheritedFieldChild.class, "inheritedValue", true);

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(InheritedFieldParent.class);
        assertThat(field.getName()).isEqualTo("inheritedValue");
    }

    @Test
    public void getFieldFindsPublicFieldDeclaredOnImplementedInterface() {
        Field field = FieldUtils.getField(InterfaceFieldTarget.class, "INTERFACE_LABEL");

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(LabelProvider.class);
        assertThat(field.getName()).isEqualTo("INTERFACE_LABEL");
    }

    @Test
    public void getAllFieldsListCollectsDeclaredFieldsFromClassHierarchy() {
        List<Field> fields = FieldUtils.getAllFieldsList(AllFieldsChild.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("childField", "parentField");
    }

    @Test
    public void readAndWriteFieldOperateOnPrivateInstanceState() throws IllegalAccessException {
        MutableFieldTarget target = new MutableFieldTarget();
        Field field = FieldUtils.getDeclaredField(MutableFieldTarget.class, "message", true);

        Object originalValue = FieldUtils.readField(field, target);
        FieldUtils.writeField(field, target, "updated");
        Object updatedValue = FieldUtils.readField(field, target);

        assertThat(originalValue).isEqualTo("initial");
        assertThat(updatedValue).isEqualTo("updated");
        assertThat(target.currentMessage()).isEqualTo("updated");
    }

    public static class DeclaredFieldTarget {
        private String hiddenValue = "declared";
    }

    public static class InheritedFieldParent {
        private String inheritedValue = "parent";
    }

    public static class InheritedFieldChild extends InheritedFieldParent {
    }

    public interface LabelProvider {
        String INTERFACE_LABEL = "shared";
    }

    public static class InterfaceFieldTarget implements LabelProvider {
    }

    public static class AllFieldsParent {
        private String parentField = "parent";
    }

    public static class AllFieldsChild extends AllFieldsParent {
        private String childField = "child";
    }

    public static class MutableFieldTarget {
        private String message = "initial";

        public String currentMessage() {
            return message;
        }
    }
}
