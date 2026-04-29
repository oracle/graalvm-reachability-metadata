/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.is;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.named;

import java.security.ProtectionDomain;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.agent.Installer;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.agent.builder.ResettableClassFileTransformer;

public class AgentBuilderInnerRedefinitionStrategyInnerResubmissionStrategyInnerEnabledInnerResubmissionInstallationListenerTest {
    @Test
    void reloadsImmediatelyResubmittedTypeByName() throws Exception {
        CapturingResubmissionScheduler scheduler = new CapturingResubmissionScheduler();
        Installer.RecordingInstrumentation instrumentation = Installer.resetInstrumentation(true);
        try {
            ResettableClassFileTransformer transformer = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .withResubmission(scheduler)
                .resubmitImmediate(is(ResubmittedType.class.getName()))
                .type(named(ResubmittedType.class.getName()))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder)
                .installOn(instrumentation);

            byte[] classFile = new ByteBuddy()
                .subclass(Object.class)
                .name(ResubmittedType.class.getName())
                .make()
                .getBytes();
            ClassLoader classLoader = ResubmittedType.class.getClassLoader();
            ProtectionDomain protectionDomain = ResubmittedType.class.getProtectionDomain();

            transformer.transform(
                classLoader,
                ResubmittedType.class.getName().replace('.', '/'),
                null,
                protectionDomain,
                classFile);
            scheduler.runCapturedTask();

            assertThat(scheduler.isCanceled()).isFalse();
            assertThat(instrumentation.getAddedTransformers()).containsExactly(transformer);
        } finally {
            Installer.resetInstrumentation();
        }
    }

    private static final class CapturingResubmissionScheduler
        implements AgentBuilder.RedefinitionStrategy.ResubmissionScheduler,
            AgentBuilder.RedefinitionStrategy.ResubmissionScheduler.Cancelable {
        private Runnable task;
        private boolean canceled;

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public AgentBuilder.RedefinitionStrategy.ResubmissionScheduler.Cancelable schedule(Runnable task) {
            this.task = task;
            return this;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        void runCapturedTask() {
            assertThat(task).isNotNull();
            task.run();
        }

        boolean isCanceled() {
            return canceled;
        }
    }

    private static final class ResubmittedType {
    }
}
