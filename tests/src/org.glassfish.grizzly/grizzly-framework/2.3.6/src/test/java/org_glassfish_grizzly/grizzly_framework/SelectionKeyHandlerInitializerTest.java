/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import static org.assertj.core.api.Assertions.assertThat;

public class SelectionKeyHandlerInitializerTest {
    private static final String DEFAULT_SELECTION_KEY_HANDLER_PROPERTY =
            "org.glassfish.grizzly.DEFAULT_SELECTION_KEY_HANDLER";

    @Test
    void defaultSelectionKeyHandlerUsesConfiguredPublicHandlerClass() {
        assertThat(System.getProperty(DEFAULT_SELECTION_KEY_HANDLER_PROPERTY))
                .isEqualTo(ConfiguredSelectionKeyHandler.class.getName());
        assertThat(SelectionKeyHandler.DEFAULT_SELECTION_KEY_HANDLER)
                .isExactlyInstanceOf(ConfiguredSelectionKeyHandler.class);
    }

    public static class ConfiguredSelectionKeyHandler implements SelectionKeyHandler {
        private final SelectionKeyHandler delegate = new DefaultSelectionKeyHandler();

        public ConfiguredSelectionKeyHandler() {
        }

        @Override
        public void onKeyRegistered(SelectionKey key) {
            delegate.onKeyRegistered(key);
        }

        @Override
        public void onKeyDeregistered(SelectionKey key) {
            delegate.onKeyDeregistered(key);
        }

        @Override
        public boolean onProcessInterest(SelectionKey key, int interest) throws IOException {
            return delegate.onProcessInterest(key, interest);
        }

        @Override
        public void cancel(SelectionKey key) throws IOException {
            delegate.cancel(key);
        }

        @Override
        public NIOConnection getConnectionForKey(SelectionKey selectionKey) {
            return delegate.getConnectionForKey(selectionKey);
        }

        @Override
        public void setConnectionForKey(NIOConnection connection, SelectionKey selectionKey) {
            delegate.setConnectionForKey(connection, selectionKey);
        }

        @Override
        public int ioEvent2SelectionKeyInterest(IOEvent ioEvent) {
            return delegate.ioEvent2SelectionKeyInterest(ioEvent);
        }

        @Override
        public IOEvent selectionKeyInterest2IoEvent(int selectionKeyInterest) {
            return delegate.selectionKeyInterest2IoEvent(selectionKeyInterest);
        }

        @Override
        public IOEvent[] getIOEvents(int interest) {
            return delegate.getIOEvents(interest);
        }
    }
}
