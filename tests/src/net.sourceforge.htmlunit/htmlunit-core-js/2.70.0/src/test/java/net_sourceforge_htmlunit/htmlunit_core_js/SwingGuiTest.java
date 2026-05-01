/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import net.sourceforge.htmlunit.corejs.javascript.tools.debugger.Dim;
import net.sourceforge.htmlunit.corejs.javascript.tools.debugger.SwingGui;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SwingGuiTest {
    @Test
    void constructorConfiguresDebuggerSplitPaneResizeWeights() throws Exception {
        SwingGui gui = createGuiOnEventDispatchThread();
        try {
            List<JSplitPane> splitPanes = new ArrayList<>();
            collectSplitPanes(gui.getContentPane(), splitPanes);

            assertThat(splitPanes)
                    .anySatisfy(splitPane -> assertResizeWeight(splitPane, 0.66d))
                    .anySatisfy(splitPane -> assertResizeWeight(splitPane, 0.5d));
        } finally {
            disposeOnEventDispatchThread(gui);
        }
    }

    private static SwingGui createGuiOnEventDispatchThread() throws Exception {
        GuiFactory factory = new GuiFactory();
        SwingUtilities.invokeAndWait(factory);
        return factory.gui;
    }

    private static void disposeOnEventDispatchThread(SwingGui gui) throws Exception {
        SwingUtilities.invokeAndWait(gui::dispose);
    }

    private static void collectSplitPanes(Component component, List<JSplitPane> splitPanes) {
        if (component instanceof JSplitPane) {
            splitPanes.add((JSplitPane) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectSplitPanes(child, splitPanes);
            }
        }
    }

    private static void assertResizeWeight(JSplitPane splitPane, double expectedWeight) {
        assertThat(splitPane.getResizeWeight()).isEqualTo(expectedWeight);
    }

    private static final class GuiFactory implements Runnable {
        private SwingGui gui;

        @Override
        public void run() {
            gui = new SwingGui(new Dim(), "Debugger Test");
        }
    }
}
