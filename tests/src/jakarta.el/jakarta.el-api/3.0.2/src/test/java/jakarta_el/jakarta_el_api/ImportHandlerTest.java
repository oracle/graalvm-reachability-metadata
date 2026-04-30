/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;

import javax.el.ImportHandler;

import org.junit.jupiter.api.Test;

public class ImportHandlerTest {

    @Test
    void resolvesClassImportedFromPackage() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importPackage("java.util");

        Class<?> resolvedClass = importHandler.resolveClass("ArrayList");

        assertThat(resolvedClass).isEqualTo(ArrayList.class);
    }

    @Test
    void resolvesExplicitlyImportedClass() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importClass(HashMap.class.getName());

        Class<?> resolvedClass = importHandler.resolveClass("HashMap");

        assertThat(resolvedClass).isEqualTo(HashMap.class);
    }

    @Test
    void returnsNullForClassMissingFromImportedPackages() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importPackage("java.util");

        Class<?> resolvedClass = importHandler.resolveClass("DefinitelyMissingElType");

        assertThat(resolvedClass).isNull();
    }
}
