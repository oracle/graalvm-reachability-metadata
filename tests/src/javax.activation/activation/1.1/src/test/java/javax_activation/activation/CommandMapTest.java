/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.security.Permission;

import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandMapTest {
    @Test
    @SuppressWarnings("removal")
    void setDefaultCommandMapAllowsSameClassLoaderWhenSetFactoryIsDenied() {
        final CommandMap previousDefaultCommandMap = CommandMap.getDefaultCommandMap();
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final CommandMap replacementCommandMap = new TestCommandMap();
        final boolean securityManagerInstalled = installSecurityManagerIfSupported();

        try {
            CommandMap.setDefaultCommandMap(replacementCommandMap);

            assertThat(CommandMap.getDefaultCommandMap()).isSameAs(replacementCommandMap);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
            CommandMap.setDefaultCommandMap(previousDefaultCommandMap);
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported() {
        try {
            System.setSecurityManager(new DenySetFactorySecurityManager());
            return true;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static final class TestCommandMap extends CommandMap {
        @Override
        public CommandInfo[] getPreferredCommands(final String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo[] getAllCommands(final String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo getCommand(final String mimeType, final String cmdName) {
            return null;
        }

        @Override
        public DataContentHandler createDataContentHandler(final String mimeType) {
            return null;
        }
    }

    @SuppressWarnings("removal")
    private static final class DenySetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("setFactory denied for coverage test");
        }
    }
}
