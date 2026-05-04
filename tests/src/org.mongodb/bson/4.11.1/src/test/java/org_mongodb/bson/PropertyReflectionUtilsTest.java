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

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyReflectionUtilsTest {
    @Test
    void classModelDiscoversDefaultInterfaceGetterFromDeclaredMethods() {
        final ClassModel<InterfacePropertyPojo> classModel = ClassModel
                .builder(InterfacePropertyPojo.class)
                .build();

        final PropertyModel<?> propertyModel = classModel.getPropertyModel("interfaceName");

        assertThat(classModel.getType()).isEqualTo(InterfacePropertyPojo.class);
        assertThat(propertyModel).isNotNull();
        assertThat(propertyModel.getName()).isEqualTo("interfaceName");
        assertThat(propertyModel.getReadName()).isEqualTo("interfaceName");
        assertThat(propertyModel.getWriteName()).isEqualTo("interfaceName");
        assertThat(propertyModel.isReadable()).isTrue();
        assertThat(propertyModel.isWritable()).isTrue();
    }

    public interface DefaultInterfaceProperty {
        default String getInterfaceName() {
            return "default-interface-name";
        }
    }

    public static final class InterfacePropertyPojo implements DefaultInterfaceProperty {
        private String interfaceName;

        public InterfacePropertyPojo() {
        }

        public void setInterfaceName(final String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getStoredInterfaceName() {
            return interfaceName;
        }
    }
}
