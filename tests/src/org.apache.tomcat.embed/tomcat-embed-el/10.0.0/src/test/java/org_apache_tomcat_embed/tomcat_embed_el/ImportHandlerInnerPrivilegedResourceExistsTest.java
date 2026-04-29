/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.security.Permission;

import jakarta.el.ImportHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ImportHandlerInnerPrivilegedResourceExistsTest {

    private static final SecurityManager PREVIOUS_SECURITY_MANAGER = System.getSecurityManager();

    static {
        System.setSecurityManager(new PermissiveSecurityManager());
    }

    @AfterAll
    static void restoreSecurityManager() {
        System.setSecurityManager(PREVIOUS_SECURITY_MANAGER);
    }

    @Test
    void resolvesImportedClassUsingPrivilegedResourceLookup() {
        ImportHandler importHandler = new ImportHandler();

        importHandler.importClass(ImportHandlerTest.StaticFieldTarget.class.getName());

        assertThat(importHandler.resolveClass(ImportHandlerTest.StaticFieldTarget.class.getSimpleName()))
                .isEqualTo(ImportHandlerTest.StaticFieldTarget.class);
    }

    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
