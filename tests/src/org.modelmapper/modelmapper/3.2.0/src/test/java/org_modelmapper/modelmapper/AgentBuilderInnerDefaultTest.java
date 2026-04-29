/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.sql.Date;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.Installer;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.agent.builder.ResettableClassFileTransformer;

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
    void installsTransformerAfterAddingReadEdgeToInstallerModule() throws Throwable {
        Installer.RecordingInstrumentation instrumentation = Installer.resetInstrumentation();
        Module originalModule = replaceAgentBuilderModule(Date.class.getModule());
        try {
            ResettableClassFileTransformer transformer = new AgentBuilder.Default().installOnByteBuddyAgent();

            assertThat(transformer).isNotNull();
            assertThat(instrumentation.getAddedTransformers()).containsExactly(transformer);
        } finally {
            replaceAgentBuilderModule(originalModule);
            Installer.resetInstrumentation();
        }
    }

    private static Module replaceAgentBuilderModule(Module module) throws ReflectiveOperationException {
        VarHandle moduleHandle = MethodHandles
            .privateLookupIn(Class.class, MethodHandles.lookup())
            .findVarHandle(Class.class, "module", Module.class);
        Module originalModule = (Module) moduleHandle.get(AgentBuilder.class);
        moduleHandle.set(AgentBuilder.class, module);
        return originalModule;
    }
}
