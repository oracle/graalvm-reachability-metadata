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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandMapTest {
    @Test
    @DisabledIfSystemProperty(named = "org.graalvm.nativeimage.imagecode", matches = "runtime")
    void setDefaultCommandMapHandlesDeniedSetFactoryFromSameClassLoader() {
        CommandMap originalCommandMap = CommandMap.getDefaultCommandMap();
        SecurityManager originalSecurityManager = System.getSecurityManager();
        MailcapCommandMap replacementCommandMap = new MailcapCommandMap();

        try {
            System.setSecurityManager(new DenyingSetFactorySecurityManager());

            CommandMap.setDefaultCommandMap(replacementCommandMap);

            assertThat(CommandMap.getDefaultCommandMap()).isSameAs(replacementCommandMap);
        } finally {
            System.setSecurityManager(originalSecurityManager);
            CommandMap.setDefaultCommandMap(originalCommandMap);
        }
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
