/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import java.math.BigDecimal;

import javax.el.ImportHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportHandlerTest {

    @Test
    void resolveClassLoadsImportedClassByName() {
        ImportHandler importHandler = new ImportHandler();
        importHandler.importClass(BigDecimal.class.getName());

        Class<?> resolvedClass = importHandler.resolveClass("BigDecimal");

        assertThat(resolvedClass).isEqualTo(BigDecimal.class);
    }

}
