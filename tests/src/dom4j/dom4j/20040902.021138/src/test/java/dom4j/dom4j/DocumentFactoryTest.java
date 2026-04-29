/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.dom4j.DocumentFactory;
import org.junit.jupiter.api.Test;

public class DocumentFactoryTest {
    @Test
    void createsDefaultFactoryWhenClassLiteralCacheIsEmpty() throws Exception {
        clearCompilerGeneratedClassCache();

        DocumentFactory factory = ExposedDocumentFactory.createDefaultFactory();

        assertThat(factory).isInstanceOf(DocumentFactory.class);
        assertThat(factory.createElement("root").getName()).isEqualTo("root");
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        Field field = DocumentFactory.class.getDeclaredField("class$org$dom4j$DocumentFactory");
        field.setAccessible(true);
        field.set(null, null);
    }
}

final class ExposedDocumentFactory extends DocumentFactory {
    private ExposedDocumentFactory() {
    }

    static DocumentFactory createDefaultFactory() {
        return createSingleton(DocumentFactory.class.getName());
    }
}
