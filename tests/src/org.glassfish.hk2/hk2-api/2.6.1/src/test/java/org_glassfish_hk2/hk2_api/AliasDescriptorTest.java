/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.Set;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.utilities.AliasDescriptor;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.junit.jupiter.api.Test;

public class AliasDescriptorTest {
    @Test
    void contractTypesResolveAliasContractWithImplementationClassLoader() {
        final ActiveDescriptor<AliasedRunnableService> descriptor = BuilderHelper.createConstantDescriptor(
                new AliasedRunnableService(), "primary", Runnable.class);
        final AliasDescriptor<AliasedRunnableService> alias = new AliasDescriptor<>(
                null, descriptor, Runnable.class.getName(), "alias");

        final Set<Type> contractTypes = alias.getContractTypes();

        assertThat(contractTypes).contains(Runnable.class);
        assertThat(alias.getImplementationClass()).isEqualTo(AliasedRunnableService.class);
    }

    private static final class AliasedRunnableService implements Runnable {
        @Override
        public void run() {
            throw new UnsupportedOperationException("The descriptor test does not execute the service");
        }
    }
}
