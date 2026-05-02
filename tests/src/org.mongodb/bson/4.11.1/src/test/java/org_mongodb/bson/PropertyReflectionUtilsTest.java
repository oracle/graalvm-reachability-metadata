/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PropertyModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyReflectionUtilsTest {
    @Test
    void classModelDiscoversPojoPropertiesFromDeclaredClassAndInterfaceMethods() {
        final ClassModel<MethodBackedPojo> classModel = ClassModel.builder(MethodBackedPojo.class).build();

        final List<String> propertyNames = classModel.getPropertyModels().stream()
                .map(PropertyModel::getName)
                .collect(Collectors.toList());

        assertThat(propertyNames).contains("active", "category", "name");
        assertThat(classModel.getPropertyModel("name")).satisfies(PropertyReflectionUtilsTest::assertReadableWritable);
        assertThat(classModel.getPropertyModel("active")).satisfies(PropertyReflectionUtilsTest::assertReadableWritable);
        assertThat(classModel.getPropertyModel("category")).satisfies(PropertyReflectionUtilsTest::assertReadableWritable);
    }

    private static void assertReadableWritable(final PropertyModel<?> propertyModel) {
        assertThat(propertyModel.isReadable()).isTrue();
        assertThat(propertyModel.isWritable()).isTrue();
    }

    public interface DefaultCategoryMethods {
        default String getCategory() {
            return "default-category";
        }

        default void setCategory(final String category) {
        }
    }

    public static final class MethodBackedPojo implements DefaultCategoryMethods {
        private String name;
        private boolean active;

        public MethodBackedPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }
    }
}
