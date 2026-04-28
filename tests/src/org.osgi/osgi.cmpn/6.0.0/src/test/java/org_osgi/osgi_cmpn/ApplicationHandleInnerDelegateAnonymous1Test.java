/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_cmpn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.application.ApplicationDescriptor;
import org.osgi.service.application.ApplicationHandle;
import org_osgi.osgi_cmpn.ApplicationDescriptorInnerDelegateAnonymous1Test.VendorApplicationDescriptor;

public class ApplicationHandleInnerDelegateAnonymous1Test {
    private static final String DESCRIPTOR_IMPLEMENTATION_PROPERTY =
            "org.osgi.vendor.application.ApplicationDescriptor";
    private static final String HANDLE_IMPLEMENTATION_PROPERTY =
            "org.osgi.vendor.application.ApplicationHandle";

    private static String previousDescriptorImplementation;
    private static String previousHandleImplementation;

    @BeforeAll
    static void setUpVendorImplementations() {
        previousDescriptorImplementation = System.getProperty(DESCRIPTOR_IMPLEMENTATION_PROPERTY);
        previousHandleImplementation = System.getProperty(HANDLE_IMPLEMENTATION_PROPERTY);
        System.setProperty(
                DESCRIPTOR_IMPLEMENTATION_PROPERTY, VendorApplicationDescriptor.class.getName());
        System.setProperty(HANDLE_IMPLEMENTATION_PROPERTY, VendorApplicationHandle.class.getName());
    }

    @AfterAll
    static void restoreVendorImplementations() {
        if (previousDescriptorImplementation == null) {
            System.clearProperty(DESCRIPTOR_IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(DESCRIPTOR_IMPLEMENTATION_PROPERTY, previousDescriptorImplementation);
        }

        if (previousHandleImplementation == null) {
            System.clearProperty(HANDLE_IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(HANDLE_IMPLEMENTATION_PROPERTY, previousHandleImplementation);
        }
    }

    @BeforeEach
    void resetVendorState() {
        VendorApplicationDescriptor.reset();
        VendorApplicationHandle.reset();
    }

    @Test
    void constructorLoadsVendorDelegateImplementationFromSystemProperty() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application");

        TestApplicationHandle handle =
                new TestApplicationHandle("example.application.1", descriptor);

        assertThat(handle.getInstanceId()).isEqualTo("example.application.1");
        assertThat(handle.getApplicationDescriptor()).isSameAs(descriptor);
        assertThat(VendorApplicationHandle.boundHandle).isSameAs(handle);
        assertThat(VendorApplicationHandle.boundDescriptorDelegate).isNotNull();
    }

    @Test
    void destroyDelegatesToVendorImplementation() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application");
        TestApplicationHandle handle =
                new TestApplicationHandle("example.application.1", descriptor);

        handle.destroy();

        assertThat(VendorApplicationHandle.destroyCalls).isEqualTo(1);
        assertThat(handle.destroySpecificCalls).isEqualTo(1);
    }

    public static final class VendorApplicationHandle {
        private static ApplicationHandle boundHandle;
        private static Object boundDescriptorDelegate;
        private static int destroyCalls;

        public static void reset() {
            boundHandle = null;
            boundDescriptorDelegate = null;
            destroyCalls = 0;
        }

        public void setApplicationHandle(ApplicationHandle handle, Object descriptorDelegate) {
            boundHandle = handle;
            boundDescriptorDelegate = descriptorDelegate;
        }

        public void destroy() {
            destroyCalls++;
        }
    }

    private static final class TestApplicationDescriptor extends ApplicationDescriptor {
        private TestApplicationDescriptor(String applicationId) {
            super(applicationId);
        }

        @Override
        public boolean matchDNChain(String pattern) {
            return false;
        }

        @Override
        protected Map getPropertiesSpecific(String locale) {
            return new HashMap();
        }

        @Override
        protected ApplicationHandle launchSpecific(Map arguments) {
            throw new AssertionError("launchSpecific should not be invoked by this coverage test");
        }

        @Override
        protected boolean isLaunchableSpecific() {
            return true;
        }

        @Override
        protected void lockSpecific() {
        }

        @Override
        protected void unlockSpecific() {
        }
    }

    private static final class TestApplicationHandle extends ApplicationHandle {
        private int destroySpecificCalls;

        private TestApplicationHandle(String instanceId, ApplicationDescriptor descriptor) {
            super(instanceId, descriptor);
        }

        @Override
        public String getState() {
            return RUNNING;
        }

        @Override
        protected void destroySpecific() {
            destroySpecificCalls++;
        }
    }
}
