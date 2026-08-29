/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_jline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.shell.jline.tui.table.BorderSpecification.FULL;
import static org.springframework.shell.jline.tui.table.BorderSpecification.LEFT;
import static org.springframework.shell.jline.tui.table.BorderSpecification.TOP;

import org.junit.jupiter.api.Test;
import org.springframework.shell.jline.tui.table.ArrayTableModel;
import org.springframework.shell.jline.tui.table.BorderStyle;
import org.springframework.shell.jline.tui.table.Table;
import org.springframework.shell.jline.tui.table.TableBuilder;

public class BorderSpecificationTest {
    private static final Object[][] DATA = {{"a", "b"}, {"c", "d"}};

    @Test
    void rendersNamedAndCombinedBorderSpecifications() {
        Table fullBorderTable = tableWithBorder(FULL);
        Table topAndLeftBorderTable = tableWithBorder(TOP | LEFT);

        assertThat(fullBorderTable.render(8)).isEqualTo("""
                +-+-+
                |a|b|
                +-+-+
                |c|d|
                +-+-+
                """);
        assertThat(topAndLeftBorderTable.render(8)).isEqualTo("""
                +--
                |ab
                |cd
                """);
    }

    private Table tableWithBorder(int match) {
        return new TableBuilder(new ArrayTableModel(DATA))
                .paintBorder(BorderStyle.oldschool, match)
                .fromTopLeft()
                .toBottomRight()
                .build();
    }
}
