/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadedTypeInitializerInnerForStaticFieldTest {
    @Test
    void initializesFixedValueFieldWhenDynamicTypeIsLoaded() throws Exception {
        try {
            Object fixedValue = new Object();

            Class<? extends Callable> dynamicType = GeneratedTypeFixtures.staticFieldCallable(fixedValue)
                    .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER.allowExistingTypes())
                    .getLoaded()
                    .asSubclass(Callable.class);

            Callable<?> callable = dynamicType.getDeclaredConstructor().newInstance();

            assertThat(callable.call()).isSameAs(fixedValue);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
