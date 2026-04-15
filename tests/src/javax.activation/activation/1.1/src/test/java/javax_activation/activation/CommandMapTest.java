/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.security.Permission;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandMapTest {
    @Test
    void setDefaultCommandMapUsesCommandMapClassLookupWhenSecurityManagerDeniesSetFactory() {
        CommandMap originalCommandMap = CommandMap.getDefaultCommandMap();
        SecurityManager originalSecurityManager = System.getSecurityManager();
        MailcapCommandMap replacementCommandMap = new MailcapCommandMap();

        try {
            Assumptions.assumeTrue(installSecurityManager(new DenyingSetFactorySecurityManager()));

            CommandMap.setDefaultCommandMap(replacementCommandMap);

            assertThat(CommandMap.getDefaultCommandMap()).isSameAs(replacementCommandMap);
        } finally {
            if (System.getSecurityManager() != originalSecurityManager) {
                restoreSecurityManager(originalSecurityManager);
            }
            CommandMap.setDefaultCommandMap(originalCommandMap);
        }
    }

    private static boolean installSecurityManager(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return true;
        } catch (UnsupportedOperationException exception) {
            return false;
        }
    }

    private static void restoreSecurityManager(SecurityManager securityManager) {
        System.setSecurityManager(securityManager);
    }

    private static final class DenyingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("setFactory denied for test coverage");
        }
    }
}
