/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractList;
import java.util.ArrayList;
import javax.el.ELException;
import javax.el.ImportHandler;
import org.junit.jupiter.api.Test;

public class ImportHandlerTest {

    @Test
    void resolvesClassImportedByFullyQualifiedName() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importClass(ArrayList.class.getName());

        Class<?> resolvedClass = importHandler.resolveClass("ArrayList");

        assertThat(resolvedClass).isEqualTo(ArrayList.class);
    }

    @Test
    void resolvesClassFromImportedPackage() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importPackage("java.util");

        Class<?> resolvedClass = importHandler.resolveClass("ArrayList");

        assertThat(resolvedClass).isEqualTo(ArrayList.class);
    }

    @Test
    void resolvesStaticMemberToDeclaringClass() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importStatic(Math.class.getName() + ".max");

        Class<?> resolvedClass = importHandler.resolveStatic("max");

        assertThat(resolvedClass).isEqualTo(Math.class);
    }

    @Test
    void returnsNullWhenImportedPackageDoesNotContainClass() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importPackage("java.util");

        Class<?> resolvedClass = importHandler.resolveClass("NoSuchImportedType");

        assertThat(resolvedClass).isNull();
    }

    @Test
    void rejectsAbstractClassesResolvedThroughImports() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importClass(AbstractList.class.getName());

        assertThatThrownBy(() -> importHandler.resolveClass("AbstractList"))
                .isInstanceOf(ELException.class)
                .hasMessageContaining("Imported class must be public");
    }
}
