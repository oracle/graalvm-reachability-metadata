/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.utility.JavaModule;

public class ResettableClassFileTransformerInnerWithDelegationInnerSubstitutableInnerFactoryInnerCreationActionTest {
    @Test
    void createsSubstitutableTransformerAndAllowsReplacingDelegate() {
        RecordingTransformer original = new RecordingTransformer();
        RecordingTransformer replacement = new RecordingTransformer();

        ResettableClassFileTransformer.Substitutable substitutable =
            (ResettableClassFileTransformer.Substitutable) AgentBuilder.TransformerDecorator.ForSubstitution.INSTANCE
                .decorate(original);

        assertThat(substitutable.unwrap()).isSameAs(original);

        substitutable.substitute(replacement);

        assertThat(substitutable.unwrap()).isSameAs(replacement);
    }

    private static final class RecordingTransformer extends ResettableClassFileTransformer.AbstractBase {
        @Override
        public Iterator<AgentBuilder.Transformer> iterator(
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule module,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain) {
            return Collections.emptyIterator();
        }

        @Override
        public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
            return classfileBuffer;
        }

        @Override
        public boolean reset(
            Instrumentation instrumentation,
            ResettableClassFileTransformer classFileTransformer,
            AgentBuilder.RedefinitionStrategy redefinitionStrategy,
            AgentBuilder.RedefinitionStrategy.DiscoveryStrategy redefinitionDiscoveryStrategy,
            AgentBuilder.RedefinitionStrategy.BatchAllocator redefinitionBatchAllocator,
            AgentBuilder.RedefinitionStrategy.Listener redefinitionListener) {
            return false;
        }
    }
}
