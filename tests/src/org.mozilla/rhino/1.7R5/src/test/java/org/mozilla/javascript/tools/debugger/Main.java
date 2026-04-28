/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.mozilla.javascript.tools.debugger;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ContextListener;

import javax.swing.JSplitPane;

public class Main implements ContextListener {
    private ContextFactory attachedFactory;

    public void attachTo(ContextFactory factory) {
        attachedFactory = factory;
    }

    public ContextFactory getAttachedFactory() {
        return attachedFactory;
    }

    public static void setResizeWeight(JSplitPane pane, double weight) {
        SwingGui.setResizeWeight(pane, weight);
    }

    @Override
    public void contextEntered(Context cx) {
    }

    @Override
    public void contextExited(Context cx) {
    }

    @Override
    public void contextCreated(Context cx) {
    }

    @Override
    public void contextReleased(Context cx) {
    }
}
