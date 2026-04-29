/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PropertyModel;
import org.junit.jupiter.api.Test;

public class PropertyReflectionUtilsTest {
    @Test
    void discoversPropertiesDeclaredAsDefaultInterfaceMethods() {
        ClassModel<InterfaceBackedPojo> classModel = ClassModel.builder(InterfaceBackedPojo.class).build();

        PropertyModel<?> propertyModel = classModel.getPropertyModel("status");

        assertThat(propertyModel).isNotNull();
        assertThat(propertyModel.isReadable()).isTrue();
        assertThat(propertyModel.isWritable()).isTrue();
    }

    public interface HasStatus {
        default String getStatus() {
            return "interface-default";
        }

        default void setStatus(String status) {
            Objects.requireNonNull(status, "status");
        }
    }

    public static class InterfaceBackedPojo implements HasStatus {
    }
}
