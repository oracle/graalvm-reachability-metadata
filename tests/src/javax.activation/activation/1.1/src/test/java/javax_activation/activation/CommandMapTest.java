/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;
import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class CommandMapTest {
    @SuppressWarnings("removal")
    @Test
    void setDefaultCommandMapAllowsSameClassLoaderAfterCheckSetFactoryFailure() {
        CommandMap originalCommandMap = CommandMap.getDefaultCommandMap();
        SecurityManager originalSecurityManager = System.getSecurityManager();
        CommandMap replacementCommandMap = new TestCommandMap();
        boolean securityManagerInstalled = false;

        try {
            securityManagerInstalled = installSecurityManagerIfSupported(new RejectingSetFactorySecurityManager());
            Assumptions.assumeTrue(securityManagerInstalled, "SecurityManager is not available on this runtime");

            CommandMap.setDefaultCommandMap(replacementCommandMap);

            assertThat(CommandMap.getDefaultCommandMap()).isSameAs(replacementCommandMap);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(originalSecurityManager);
            }
            CommandMap.setDefaultCommandMap(originalCommandMap);
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return true;
        } catch (SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static final class TestCommandMap extends CommandMap {
        @Override
        public CommandInfo[] getPreferredCommands(String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo[] getAllCommands(String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo getCommand(String mimeType, String cmdName) {
            return null;
        }

        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            return null;
        }
    }

    @SuppressWarnings("removal")
    private static final class RejectingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkSetFactory() {
            throw new SecurityException("factory changes are rejected for this test");
        }

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
