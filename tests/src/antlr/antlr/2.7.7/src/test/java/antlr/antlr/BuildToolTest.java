/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.build.Tool;
import antlr.build.ToolCoverageAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildToolTest {

    @Test
    void loadsTheToolClassThroughItsSyntheticClassLiteralHelper() {
        assertThat(ToolCoverageAccess.loadToolClassLiteralBackingMethod()).isEqualTo(Tool.class);
    }
}
