/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.FactoryDescriptors;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.TwoPhaseResource;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;

public class AbstractBinderAnonymous2Test {
    @Test
    void defaultBinderLoaderCanLoadClassForBoundDescriptor() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        descriptor.setImplementation(DescriptorImpl.class.getName());
        descriptor.addAdvertisedContract(DescriptorImpl.class.getName());
        final RecordingDynamicConfiguration configuration = new RecordingDynamicConfiguration();
        final AbstractBinder binder = new AbstractBinder() {
            @Override
            protected void configure() {
                bind(descriptor);
            }
        };

        binder.bind(configuration);

        assertThat(configuration.boundDescriptor).isSameAs(descriptor);
        assertThat(configuration.boundDescriptor.getLoader()).isNotNull();
        final Class<?> loadedClass = configuration.boundDescriptor.getLoader()
                .loadClass(DescriptorImpl.class.getName());
        assertThat(loadedClass).isSameAs(DescriptorImpl.class);
    }

    private static final class RecordingDynamicConfiguration implements DynamicConfiguration {
        private Descriptor boundDescriptor;

        @Override
        public <T> ActiveDescriptor<T> bind(Descriptor key) {
            return bind(key, true);
        }

        @Override
        public <T> ActiveDescriptor<T> bind(Descriptor key, boolean requiresDeepCopy) {
            boundDescriptor = key;
            return null;
        }

        @Override
        public FactoryDescriptors bind(FactoryDescriptors factoryDescriptors) {
            return bind(factoryDescriptors, true);
        }

        @Override
        public FactoryDescriptors bind(FactoryDescriptors factoryDescriptors, boolean requiresDeepCopy) {
            throw new UnsupportedOperationException("Factory descriptor binding is not used by this test");
        }

        @Override
        public <T> ActiveDescriptor<T> addActiveDescriptor(ActiveDescriptor<T> activeDescriptor)
                throws IllegalArgumentException {
            throw new UnsupportedOperationException("Active descriptor binding is not used by this test");
        }

        @Override
        public <T> ActiveDescriptor<T> addActiveDescriptor(ActiveDescriptor<T> activeDescriptor,
                boolean requiresDeepCopy) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Active descriptor binding is not used by this test");
        }

        @Override
        public <T> ActiveDescriptor<T> addActiveDescriptor(Class<T> rawClass)
                throws MultiException, IllegalArgumentException {
            throw new UnsupportedOperationException("Class-based active descriptor binding is not used by this test");
        }

        @Override
        public <T> FactoryDescriptors addActiveFactoryDescriptor(Class<? extends Factory<T>> rawFactoryClass)
                throws MultiException, IllegalArgumentException {
            throw new UnsupportedOperationException("Active factory descriptor binding is not used by this test");
        }

        @Override
        public void addUnbindFilter(Filter unbindFilter) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Unbind filters are not used by this test");
        }

        @Override
        public void addIdempotentFilter(Filter... unbindFilter) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Idempotent filters are not used by this test");
        }

        @Override
        public void registerTwoPhaseResources(TwoPhaseResource... resources) {
            throw new UnsupportedOperationException("Two-phase resources are not used by this test");
        }

        @Override
        public void commit() throws MultiException {
            throw new UnsupportedOperationException("Commit is not used by this test");
        }
    }
}
