/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.tools.debugger.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextTest {
    @SuppressWarnings("deprecation")
    @Test
    void addContextListenerAttachesDebuggerMainThroughCompatibilityPath() {
        Main debugger = new Main();

        Context.addContextListener(debugger);

        assertThat(debugger.getAttachedFactory()).isSameAs(ContextFactory.getGlobal());
    }
}
