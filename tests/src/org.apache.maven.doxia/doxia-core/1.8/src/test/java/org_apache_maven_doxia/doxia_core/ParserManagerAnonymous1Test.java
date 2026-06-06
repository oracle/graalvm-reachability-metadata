/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_core;

import org.apache.maven.doxia.parser.manager.ParserManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserManagerAnonymous1Test {
    @Test
    void roleInitializesParserManagerInterface() {
        assertThat(ParserManager.ROLE).isEqualTo("org.apache.maven.doxia.parser.manager.ParserManager");
    }
}
