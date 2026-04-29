/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ImportHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportHandlerTest {

    @Test
    void resolvesImportedPublicStaticFieldToItsDeclaringClass() {
        ImportHandler importHandler = new ImportHandler();

        importHandler.importStatic(StaticFieldTarget.class.getName() + ".GREETING");

        assertThat(importHandler.resolveStatic("GREETING")).isEqualTo(StaticFieldTarget.class);
    }

    @Test
    void resolvesImportedPublicStaticMethodToItsDeclaringClass() {
        ImportHandler importHandler = new ImportHandler();

        importHandler.importStatic(StaticMethodTarget.class.getName() + ".describe");

        assertThat(importHandler.resolveStatic("describe")).isEqualTo(StaticMethodTarget.class);
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
        }
    }

    public static final class StaticMethodTarget {
        private StaticMethodTarget() {
        }

        public static String describe(String value) {
            return "static:" + value;
        }
    }
}
