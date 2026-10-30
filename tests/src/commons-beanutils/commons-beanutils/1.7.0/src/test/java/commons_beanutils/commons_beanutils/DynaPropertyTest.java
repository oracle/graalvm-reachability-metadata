/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaProperty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynaPropertyTest {
    @Test
    void persistsSerializablePropertyDescriptorsWithTheirTypes() throws Exception {
        DynaProperty[] properties = {
                new DynaProperty("title", String.class),
                new DynaProperty("count", int.class),
                new DynaProperty("items", List.class, String.class),
                new DynaProperty("attributes", Map.class, Integer.class)
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            for (DynaProperty property : properties) {
                output.writeObject(property);
            }
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            for (DynaProperty property : properties) {
                DynaProperty restored = (DynaProperty) input.readObject();
                assertEquals(property.getName(), restored.getName());
                assertEquals(property.getType(), restored.getType());
                assertEquals(property.getContentType(), restored.getContentType());
                assertEquals(property.isIndexed(), restored.isIndexed());
                assertEquals(property.isMapped(), restored.isMapped());
            }
        }
        assertFalse(properties[0].isIndexed());
        assertTrue(properties[2].isIndexed());
        assertTrue(properties[3].isMapped());
    }
}
