/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.service.application.ApplicationDescriptor;
import org.osgi.service.application.ApplicationHandle;
import org.osgi.service.application.ApplicationException;
import org.osgi.service.application.ScheduledApplication;

public class ApplicationDescriptorDelegate1Test {
    private static final String IMPLEMENTATION_PROPERTY =
            "org.osgi.vendor.application.ApplicationDescriptor";

    @Test
    void constructorLoadsVendorDelegateImplementationFromSystemProperty() {
        String previousImplementation = System.getProperty(IMPLEMENTATION_PROPERTY);
        System.setProperty(IMPLEMENTATION_PROPERTY, VendorApplicationDescriptor.class.getName());

        try {
            VendorApplicationDescriptor.reset();

            TestApplicationDescriptor descriptor = new TestApplicationDescriptor("example.application");

            assertThat(descriptor.getApplicationId()).isEqualTo("example.application");
            assertThat(VendorApplicationDescriptor.boundApplicationId).isEqualTo("example.application");
            assertThat(VendorApplicationDescriptor.boundDescriptor).isSameAs(descriptor);
        } finally {
            if (previousImplementation == null) {
                System.clearProperty(IMPLEMENTATION_PROPERTY);
            } else {
                System.setProperty(IMPLEMENTATION_PROPERTY, previousImplementation);
            }
        }
    }

    public static final class VendorApplicationDescriptor {
        private static ApplicationDescriptor boundDescriptor;
        private static String boundApplicationId;

        public static void reset() {
            boundDescriptor = null;
            boundApplicationId = null;
        }

        public void setApplicationDescriptor(ApplicationDescriptor descriptor, String applicationId) {
            boundDescriptor = descriptor;
            boundApplicationId = applicationId;
        }

        public boolean isLocked() {
            return false;
        }

        public void lock() {
        }

        public void unlock() {
        }

        public ScheduledApplication schedule(
                String scheduleId,
                Map arguments,
                String topic,
                String eventFilter,
                boolean recurring) {
            return null;
        }

        public ApplicationHandle launch(Map arguments) {
            return null;
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
            return Collections.emptyMap();
        }

        @Override
        protected ApplicationHandle launchSpecific(Map arguments) throws Exception {
            throw new ApplicationException(
                    ApplicationException.APPLICATION_INTERNAL_ERROR,
                    "launchSpecific should not be invoked by this coverage test");
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
}
