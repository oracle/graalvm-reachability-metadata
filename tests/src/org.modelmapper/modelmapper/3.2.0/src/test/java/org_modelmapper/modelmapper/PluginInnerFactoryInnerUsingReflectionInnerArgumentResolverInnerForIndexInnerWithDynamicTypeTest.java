/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.build.Plugin;
import org.modelmapper.spi.PropertyType;

public class PluginInnerFactoryInnerUsingReflectionInnerArgumentResolverInnerForIndexInnerWithDynamicTypeTest {
    @Test
    void resolvesEnumArgumentUsingStaticValueOfMethodForRequestedDynamicType() {
        Plugin.Factory.UsingReflection.ArgumentResolver resolver =
                new Plugin.Factory.UsingReflection.ArgumentResolver.ForIndex.WithDynamicType(0, "FIELD");

        Plugin.Factory.UsingReflection.ArgumentResolver.Resolution resolution = resolver.resolve(0, PropertyType.class);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.getArgument()).isEqualTo(PropertyType.FIELD);
    }
}
