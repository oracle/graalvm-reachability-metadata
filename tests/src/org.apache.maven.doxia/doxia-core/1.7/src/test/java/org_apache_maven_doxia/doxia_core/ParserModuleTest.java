/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_core;

import org.apache.maven.doxia.parser.module.AbstractParserModule;
import org.apache.maven.doxia.parser.module.ParserModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserModuleTest {
    @Test
    void abstractParserModuleExposesParserModuleMetadata() {
        ParserModule parserModule = new DocumentationParserModule();

        assertThat(parserModule.getSourceDirectory()).isEqualTo("documentation");
        assertThat(parserModule.getParserId()).isEqualTo("xdoc");
        assertThat(parserModule.getExtensions()).containsExactly("xdoc", "xml");
    }

    private static final class DocumentationParserModule extends AbstractParserModule {
        DocumentationParserModule() {
            super("documentation", "xdoc", "xdoc", "xml");
        }
    }
}
