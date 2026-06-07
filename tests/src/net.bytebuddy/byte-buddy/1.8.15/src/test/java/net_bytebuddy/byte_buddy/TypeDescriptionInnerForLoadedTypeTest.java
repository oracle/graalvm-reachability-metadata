/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerForLoadedTypeTest {
    @Test
    void describesDeclaredMemberTypesOfLoadedClass() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(DeclaredMemberCarrier.class);

        TypeList declaredTypes = typeDescription.getDeclaredTypes();

        assertThat(declaredTypes)
                .extracting(TypeDescription::getName)
                .contains(
                        DeclaredMemberCarrier.NestedOne.class.getName(),
                        DeclaredMemberCarrier.NestedTwo.class.getName());
    }

    @Test
    void describesDeclaredFieldsOfLoadedClass() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(DeclaredMemberCarrier.class);

        FieldList<FieldDescription.InDefinedShape> declaredFields = typeDescription.getDeclaredFields();

        assertThat(declaredFields)
                .extracting(FieldDescription::getName)
                .contains("label", "count");
    }

    private static class DeclaredMemberCarrier {
        private final String label = "byte-buddy";

        private int count;

        private static class NestedOne {
        }

        private interface NestedTwo {
        }
    }
}
