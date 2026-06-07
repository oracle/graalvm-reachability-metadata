/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypePoolInnerClassLoadingTest {
    @Test
    void describesTypeByLoadingClassFromProvidedClassLoader() {
        TypePool typePool = TypePool.ClassLoading.of(getClass().getClassLoader());

        TypeDescription typeDescription = typePool.describe(SampleType.class.getName()).resolve();

        assertThat(typeDescription.represents(SampleType.class)).isTrue();
        assertThat(typeDescription.getDeclaredMethods())
                .extracting(methodDescription -> methodDescription.getName())
                .contains("message");
    }

    public static class SampleType {
        public String message() {
            return "byte-buddy";
        }
    }
}
