/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import java.util.ArrayList;

import javax.el.ImportHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportHandlerTest {

    @Test
    void resolvesImportedClassBySimpleName() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importClass(ArrayList.class.getName());

        Class<?> resolvedClass = importHandler.resolveClass("ArrayList");

        assertThat(resolvedClass).isEqualTo(ArrayList.class);
    }

    @Test
    void resolvesImportedStaticMemberToItsDeclaringClass() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importStatic(Math.class.getName() + ".max");

        Class<?> resolvedClass = importHandler.resolveStatic("max");

        assertThat(resolvedClass).isEqualTo(Math.class);
    }
}
