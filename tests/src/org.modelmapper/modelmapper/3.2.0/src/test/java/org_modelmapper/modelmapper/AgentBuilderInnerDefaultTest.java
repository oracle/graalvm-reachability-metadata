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
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.modelmapper.internal.bytebuddy.utility.JavaModuleAccess;

public class AgentBuilderInnerDefaultTest {
    @Test
    void installsTransformerUsingByteBuddyAgentInstaller() {
        Installer.RecordingInstrumentation instrumentation = Installer.resetInstrumentation();
        try {
            ResettableClassFileTransformer transformer = new AgentBuilder.Default().installOnByteBuddyAgent();

            assertThat(transformer).isNotNull();
            assertThat(instrumentation.getAddedTransformers()).containsExactly(transformer);
        } finally {
            Installer.resetInstrumentation();
        }
    }

    @Test
    void installsTransformerAfterAddingReadEdgeToInstallerModule() throws Exception {
        Installer.RecordingInstrumentation instrumentation = Installer.resetInstrumentation();
        try (JavaModuleAccess.Reset ignored = JavaModuleAccess.forceUnreadableModules()) {
            ResettableClassFileTransformer transformer = new AgentBuilder.Default().installOnByteBuddyAgent();

            assertThat(transformer).isNotNull();
            assertThat(instrumentation.getAddedTransformers()).containsExactly(transformer);
        } finally {
            Installer.resetInstrumentation();
        }
    }
}
