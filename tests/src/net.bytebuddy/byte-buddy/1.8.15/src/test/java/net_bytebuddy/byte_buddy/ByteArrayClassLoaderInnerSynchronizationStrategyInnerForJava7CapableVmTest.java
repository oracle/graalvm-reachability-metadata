/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest {
    private static final String TYPE_NAME = "net_bytebuddy.byte_buddy.generated.Java7SynchronizedType";

    @Test
    void invokesConfiguredClassLoadingLockMethod() throws Exception {
        ExposedByteArrayClassLoader classLoader = new ExposedByteArrayClassLoader();

        Object directLock = classLoader.exposedClassLoadingLock(TYPE_NAME);
        Object strategyLock = classLoader.getClassLoadingLockThroughJava7Strategy(TYPE_NAME);

        assertThat(strategyLock).isSameAs(directLock);
    }

    private static class ExposedByteArrayClassLoader extends ByteArrayClassLoader {
        ExposedByteArrayClassLoader() {
            super(ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest.class.getClassLoader(),
                    false,
                    Collections.<String, byte[]>emptyMap());
        }

        public Object exposedClassLoadingLock(String name) {
            return getClassLoadingLock(name);
        }

        Object getClassLoadingLockThroughJava7Strategy(String name) throws Exception {
            Method method = ExposedByteArrayClassLoader.class.getMethod("exposedClassLoadingLock", String.class);
            SynchronizationStrategy strategy = new Java7SynchronizationStrategy(method).initialize();
            return strategy.getClassLoadingLock(this, name);
        }

        private static class Java7SynchronizationStrategy extends SynchronizationStrategy.ForJava7CapableVm {
            Java7SynchronizationStrategy(Method method) {
                super(method);
            }
        }
    }
}
