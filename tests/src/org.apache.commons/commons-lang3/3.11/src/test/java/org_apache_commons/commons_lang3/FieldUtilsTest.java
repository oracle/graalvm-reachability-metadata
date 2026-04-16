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
    void resolvesInheritedAndInterfaceFieldsThroughPublicLookups() {
        final Field inheritedField = FieldUtils.getField(FieldChild.class, "hiddenValue", true);
        final Field interfaceField = FieldUtils.getField(FieldChild.class, "INTERFACE_VALUE");

        assertThat(inheritedField).isNotNull();
        assertThat(inheritedField.getDeclaringClass()).isEqualTo(FieldParent.class);
        assertThat(inheritedField.getName()).isEqualTo("hiddenValue");
        assertThat(interfaceField).isNotNull();
        assertThat(interfaceField.getDeclaringClass()).isEqualTo(HasInterfaceField.class);
        assertThat(interfaceField.getName()).isEqualTo("INTERFACE_VALUE");
    }

    @Test
    void resolvesOnlyDeclaredFieldsFromTheRequestedClass() {
        final Field declaredField = FieldUtils.getDeclaredField(DeclaredFieldHolder.class, "declaredValue", true);
        final Field inheritedField = FieldUtils.getDeclaredField(DeclaredFieldChild.class, "declaredValue", true);

        assertThat(declaredField).isNotNull();
        assertThat(declaredField.getDeclaringClass()).isEqualTo(DeclaredFieldHolder.class);
        assertThat(inheritedField).isNull();
    }

    @Test
    void collectsAllFieldsFromTheClassHierarchy() {
        final List<Field> fields = FieldUtils.getAllFieldsList(AllFieldsChild.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("childValue", "parentVisibleValue", "parentHiddenValue");
    }

    @Test
    void readsAndWritesPublicAndDeclaredFields() throws IllegalAccessException {
        final ReadWriteHolder holder = new ReadWriteHolder();

        assertThat(FieldUtils.readField(holder, "publicValue")).isEqualTo("public");
        assertThat(FieldUtils.readDeclaredField(holder, "secretValue", true)).isEqualTo("secret");

        FieldUtils.writeField(holder, "publicValue", "updated-public");
        FieldUtils.writeDeclaredField(holder, "secretValue", "updated-secret", true);

        assertThat(holder.publicValue).isEqualTo("updated-public");
        assertThat(FieldUtils.readDeclaredField(holder, "secretValue", true)).isEqualTo("updated-secret");
    }

    public interface HasInterfaceField {
        String INTERFACE_VALUE = "interface";
    }

    public static class FieldParent {
        private String hiddenValue = "hidden";
    }

    public static class FieldChild extends FieldParent implements HasInterfaceField {
    }

    public static class DeclaredFieldHolder {
        private String declaredValue = "declared";
    }

    public static class DeclaredFieldChild extends DeclaredFieldHolder {
        private String childValue = "child";
    }

    public static class AllFieldsParent {
        public String parentVisibleValue = "parent-visible";
        private String parentHiddenValue = "parent-hidden";
    }

    public static class AllFieldsChild extends AllFieldsParent {
        private String childValue = "child";
    }

    public static class ReadWriteHolder {
        public String publicValue = "public";
        private String secretValue = "secret";
    }
}
