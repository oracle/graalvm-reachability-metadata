/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.tools.debugger.Main;

import javax.swing.JSplitPane;

import static org.assertj.core.api.Assertions.assertThat;

public class SwingGuiTest {
    @Test
    void appliesSplitPaneResizeWeightThroughDebuggerUtility() {
        JSplitPane pane = new JSplitPane();

        Main.setResizeWeight(pane, 0.42D);

        assertThat(pane.getResizeWeight()).isEqualTo(0.42D);
    }
}
