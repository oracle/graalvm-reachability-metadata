/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.pool.TypePool;

public class TypePoolInnerClassLoadingTest {
    @Test
    void describesAlreadyLoadedTypeThroughClassLoadingPool() {
        TypePool typePool = TypePool.ClassLoading.of(TypePoolInnerClassLoadingTest.class.getClassLoader());

        TypePool.Resolution resolution = typePool.describe(TypePoolInnerClassLoadingTest.class.getName());

        assertThat(resolution.isResolved()).isTrue();
        TypeDescription typeDescription = resolution.resolve();
        assertThat(typeDescription.represents(TypePoolInnerClassLoadingTest.class)).isTrue();
        assertThat(typeDescription.getName()).isEqualTo(TypePoolInnerClassLoadingTest.class.getName());
    }
}
