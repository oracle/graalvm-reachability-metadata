/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections_api;

import org.eclipse.collections.api.factory.ServiceLoaderUtils;
import org.eclipse.collections.api.factory.list.ImmutableListFactory;
import org.eclipse.collections.api.factory.list.MutableListFactory;
import org.eclipse.collections.impl.list.mutable.MutableListFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class ServiceLoaderUtilsTest {
    @Test
    void loadsMappedFactoryByReflectionWhenFallbackImplementationIsAvailable() {
        MutableListFactory factory = ServiceLoaderUtils.loadServiceClass(MutableListFactory.class);

        assertThat(factory).isInstanceOf(MutableListFactoryImpl.class);
    }

    @Test
    void createsThrowingProxyWhenServiceAndFallbackImplementationAreUnavailable() {
        ImmutableListFactory factory = ServiceLoaderUtils.loadServiceClass(ImmutableListFactory.class);

        assertThat(factory).isInstanceOf(ImmutableListFactory.class);
        assertThatIllegalStateException()
                .isThrownBy(factory::empty)
                .withMessageContaining("Could not find any implementations of ImmutableListFactory");
    }
}
