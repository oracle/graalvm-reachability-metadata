/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.utility.JavaModule;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

public class AgentBuilderInnerRedefinitionStrategyInnerResubmissionStrategyInnerEnabledInnerResubmissionInstallationListenerTest {
    private static final String GENERATED_TYPE_NAME = "net_bytebuddy.byte_buddy.generated.ResubmittedType";
    private static final RuntimeException INITIAL_TRANSFORMATION_FAILURE = new IllegalStateException(
            "defer transformation until resubmission");

    @Test
    void resubmitsPreviouslyFailedUnloadedType() {
        try {
            ManualResubmissionScheduler scheduler = new ManualResubmissionScheduler();
            AtomicBoolean failInitialTransformation = new AtomicBoolean(true);
            AtomicInteger transformations = new AtomicInteger();

            ResettableClassFileTransformer transformer = new AgentBuilder.Default()
                    .disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .withResubmission(scheduler, is(INITIAL_TRANSFORMATION_FAILURE))
                    .type(named(GENERATED_TYPE_NAME))
                    .transform(new AgentBuilder.Transformer() {
                        @Override
                        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                                TypeDescription typeDescription,
                                                                ClassLoader classLoader,
                                                                JavaModule module) {
                            transformations.incrementAndGet();
                            if (failInitialTransformation.compareAndSet(true, false)) {
                                throw INITIAL_TRANSFORMATION_FAILURE;
                            }
                            return builder;
                        }
                    })
                    .installOnByteBuddyAgent();
            assertThat(transformer).isNotNull();
            assertThat(scheduler.scheduled).isNotNull();

            Class<?> loadedType = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name(GENERATED_TYPE_NAME)
                    .make()
                    .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded();

            assertThat(loadedType.getName()).isEqualTo(GENERATED_TYPE_NAME);
            assertThat(transformations.get()).isEqualTo(1);

            scheduler.runScheduledJob();

            assertThat(transformations.get()).isGreaterThanOrEqualTo(2);
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessageContaining("The Byte Buddy agent is not installed or not accessible");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ManualResubmissionScheduler
            implements AgentBuilder.RedefinitionStrategy.ResubmissionScheduler {
        private Runnable scheduled;
        private boolean alive = true;

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Cancelable schedule(Runnable job) {
            scheduled = job;
            return new Cancelable() {
                @Override
                public void cancel() {
                    alive = false;
                }
            };
        }

        private void runScheduledJob() {
            assertThat(scheduled).isNotNull();
            scheduled.run();
        }
    }
}
