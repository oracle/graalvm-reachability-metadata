/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr;

import org.antlr.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolTest {
    @Test
    void constructorLoadsBundledAntlrSettings() {
        Tool tool = new Tool();

        assertThat(tool.antlrSettings)
                .containsKey("antlr.version");
        assertThat(tool.antlrSettings.getProperty("antlr.version"))
                .isNotBlank();
        assertThat(tool.VERSION)
                .isNotBlank();
    }
}
