/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.concurrent.FutureTask;

import org.dom4j.DocumentFactory;
import org.junit.jupiter.api.Test;

public class DocumentFactoryTest {
    @Test
    void getInstanceCreatesDefaultFactoryWhenClassLiteralCacheIsEmpty() throws Exception {
        clearCompilerGeneratedClassCache();

        FutureTask<DocumentFactory> task = new FutureTask<>(DocumentFactory::getInstance);
        Thread thread = new Thread(task);
        thread.start();
        DocumentFactory factory = task.get();

        assertThat(factory).isInstanceOf(DocumentFactory.class);
        assertThat(factory.createElement("root").getName()).isEqualTo("root");
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        Field field = DocumentFactory.class.getDeclaredField("class$org$dom4j$DocumentFactory");
        field.setAccessible(true);
        field.set(null, null);
    }
}
