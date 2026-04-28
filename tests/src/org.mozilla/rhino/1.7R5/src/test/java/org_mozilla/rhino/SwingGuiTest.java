/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import javax.swing.JSplitPane;

import static org.assertj.core.api.Assertions.assertThat;

public class SwingGuiTest {
    @Test
    void scriptAppliesSplitPaneResizeWeightThroughDebuggerUtility() {
        JSplitPane pane = new JSplitPane();

        Object result = new EnhancedJavaAccessFactory().call((ContextAction) cx -> {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "pane", Context.javaToJS(pane, scope));

            return cx.evaluateString(
                    scope,
                    "Packages.org.mozilla.javascript.tools.debugger.SwingGui.setResizeWeight(pane, 0.42);"
                            + "pane.getResizeWeight();",
                    "swingGuiResizeWeightCoverage",
                    1,
                    null);
        });

        assertThat(Context.toNumber(result)).isEqualTo(0.42D);
        assertThat(pane.getResizeWeight()).isEqualTo(0.42D);
    }

    private static final class EnhancedJavaAccessFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_ENHANCED_JAVA_ACCESS) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }
}
