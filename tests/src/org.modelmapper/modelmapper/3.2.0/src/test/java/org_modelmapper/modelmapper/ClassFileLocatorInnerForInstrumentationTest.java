/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.Installer;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator;
import org.modelmapper.internal.bytebuddy.utility.JavaModuleAccess;

public class ClassFileLocatorInnerForInstrumentationTest {
    @Test
    void resolvesInstrumentationFromInstalledByteBuddyAgent() throws Exception {
        Installer.resetInstrumentation(true);
        try {
            ClassFileLocator locator = ClassFileLocator.ForInstrumentation.fromInstalledAgent(getClass().getClassLoader());

            assertThat(locator).isNotNull();
            locator.close();
        } finally {
            Installer.resetInstrumentation();
        }
    }

    @Test
    void resolvesInstrumentationAfterAddingReadEdgeToInstallerModule() throws Exception {
        Installer.resetInstrumentation(true);
        try (JavaModuleAccess.Reset ignored = JavaModuleAccess.forceUnreadableModules()) {
            ClassFileLocator locator = ClassFileLocator.ForInstrumentation.fromInstalledAgent(getClass().getClassLoader());

            assertThat(locator).isNotNull();
            locator.close();
        } finally {
            Installer.resetInstrumentation();
        }
    }
}
