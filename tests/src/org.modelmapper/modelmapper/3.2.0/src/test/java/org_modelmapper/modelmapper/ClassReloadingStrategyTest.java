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
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.modelmapper.internal.bytebuddy.utility.JavaModuleAccess;

public class ClassReloadingStrategyTest {
    @Test
    void createsReloadingStrategyFromInstalledAgent() {
        Installer.resetInstrumentation(true);
        try {
            ClassReloadingStrategy strategy = ClassReloadingStrategy.fromInstalledAgent();

            assertThat(strategy).isNotNull();
        } finally {
            Installer.resetInstrumentation();
        }
    }

    @Test
    void addsReadEdgeBeforeResolvingAgentInstrumentationWhenModulesAppearUnreadable() throws Exception {
        Installer.resetInstrumentation(true);
        try (JavaModuleAccess.Reset ignored = JavaModuleAccess.forceUnreadableModules()) {
            ClassReloadingStrategy strategy = ClassReloadingStrategy.fromInstalledAgent();

            assertThat(strategy).isNotNull();
        } finally {
            Installer.resetInstrumentation();
        }
    }
}
