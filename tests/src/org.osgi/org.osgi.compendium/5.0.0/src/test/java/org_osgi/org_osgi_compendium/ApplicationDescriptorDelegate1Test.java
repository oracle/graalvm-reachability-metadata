/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.application.ApplicationDescriptor;
import org.osgi.service.application.ApplicationHandle;
import org.osgi.service.application.ApplicationException;
import org.osgi.service.application.ScheduledApplication;
import org.osgi.framework.InvalidSyntaxException;

public class ApplicationDescriptorDelegate1Test {
    private static final String IMPLEMENTATION_PROPERTY =
            "org.osgi.vendor.application.ApplicationDescriptor";
    private static String previousImplementation;

    @BeforeAll
    static void setUpVendorImplementation() {
        previousImplementation = System.getProperty(IMPLEMENTATION_PROPERTY);
        System.setProperty(IMPLEMENTATION_PROPERTY, VendorApplicationDescriptor.class.getName());
    }

    @AfterAll
    static void restoreVendorImplementation() {
        if (previousImplementation == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, previousImplementation);
        }
    }

    @BeforeEach
    void resetVendorState() {
        VendorApplicationDescriptor.reset();
    }

    @Test
    void constructorLoadsVendorDelegateImplementationFromSystemProperty() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, true);

        assertThat(descriptor.getApplicationId()).isEqualTo("example.application");
        assertThat(VendorApplicationDescriptor.boundApplicationId).isEqualTo("example.application");
        assertThat(VendorApplicationDescriptor.boundDescriptor).isSameAs(descriptor);
    }

    @Test
    void getPropertiesUsesVendorIsLockedState() {
        VendorApplicationDescriptor.locked = true;

        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, true);

        Map properties = descriptor.getProperties(null);

        assertThat(properties).containsEntry(ApplicationDescriptor.APPLICATION_LOCKED, Boolean.TRUE);
        assertThat(VendorApplicationDescriptor.isLockedCalls).isEqualTo(1);
        assertThat(descriptor.lockSpecificCalls).isEqualTo(1);
        assertThat(descriptor.unlockSpecificCalls).isZero();
    }

    @Test
    void lockDelegatesToVendorImplementation() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, true);

        descriptor.lock();

        assertThat(VendorApplicationDescriptor.lockCalls).isEqualTo(1);
        assertThat(descriptor.lockSpecificCalls).isEqualTo(1);
    }

    @Test
    void unlockDelegatesToVendorImplementation() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, true);

        descriptor.unlock();

        assertThat(VendorApplicationDescriptor.unlockCalls).isEqualTo(1);
        assertThat(descriptor.unlockSpecificCalls).isEqualTo(1);
    }

    @Test
    void scheduleDelegatesToVendorImplementation() throws ApplicationException, InvalidSyntaxException {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, true);
        Map arguments = new HashMap();
        arguments.put("mode", "full");

        ScheduledApplication scheduledApplication =
                descriptor.schedule("nightly", arguments, "app/topic", "(mode=full)", true);

        assertThat(VendorApplicationDescriptor.scheduleCalls).isEqualTo(1);
        assertThat(VendorApplicationDescriptor.lastScheduleId).isEqualTo("nightly");
        assertThat(VendorApplicationDescriptor.lastArguments).isSameAs(arguments);
        assertThat(VendorApplicationDescriptor.lastTopic).isEqualTo("app/topic");
        assertThat(VendorApplicationDescriptor.lastEventFilter).isEqualTo("(mode=full)");
        assertThat(VendorApplicationDescriptor.lastRecurring).isTrue();
        assertThat(scheduledApplication).isSameAs(VendorApplicationDescriptor.lastScheduledApplication);
    }

    @Test
    void launchInvokesVendorDelegateBeforeLaunchabilityCheck() {
        TestApplicationDescriptor descriptor =
                new TestApplicationDescriptor("example.application", false, false);
        Map arguments = new HashMap();
        arguments.put("mode", "dry-run");

        ApplicationException applicationException = null;
        try {
            descriptor.launch(arguments);
        } catch (ApplicationException ex) {
            applicationException = ex;
        }

        assertThat(applicationException).isNotNull();
        assertThat(applicationException.getErrorCode())
                .isEqualTo(ApplicationException.APPLICATION_NOT_LAUNCHABLE);
        assertThat(applicationException).hasMessage("Cannot launch the application!");
        assertThat(VendorApplicationDescriptor.launchCalls).isEqualTo(1);
        assertThat(VendorApplicationDescriptor.lastLaunchArguments).isSameAs(arguments);
    }

    public static final class VendorApplicationDescriptor {
        private static ApplicationDescriptor boundDescriptor;
        private static String boundApplicationId;
        private static boolean locked;
        private static int isLockedCalls;
        private static int lockCalls;
        private static int unlockCalls;
        private static int scheduleCalls;
        private static int launchCalls;
        private static String lastScheduleId;
        private static Map lastArguments;
        private static String lastTopic;
        private static String lastEventFilter;
        private static boolean lastRecurring;
        private static ScheduledApplication lastScheduledApplication;
        private static Map lastLaunchArguments;

        public static void reset() {
            boundDescriptor = null;
            boundApplicationId = null;
            locked = false;
            isLockedCalls = 0;
            lockCalls = 0;
            unlockCalls = 0;
            scheduleCalls = 0;
            launchCalls = 0;
            lastScheduleId = null;
            lastArguments = null;
            lastTopic = null;
            lastEventFilter = null;
            lastRecurring = false;
            lastScheduledApplication = null;
            lastLaunchArguments = null;
        }

        public void setApplicationDescriptor(ApplicationDescriptor descriptor, String applicationId) {
            boundDescriptor = descriptor;
            boundApplicationId = applicationId;
        }

        public boolean isLocked() {
            isLockedCalls++;
            return locked;
        }

        public void lock() {
            lockCalls++;
        }

        public void unlock() {
            unlockCalls++;
        }

        public ScheduledApplication schedule(
                String scheduleId,
                Map arguments,
                String topic,
                String eventFilter,
                boolean recurring) {
            scheduleCalls++;
            lastScheduleId = scheduleId;
            lastArguments = arguments;
            lastTopic = topic;
            lastEventFilter = eventFilter;
            lastRecurring = recurring;
            lastScheduledApplication =
                    new TestScheduledApplication(
                            scheduleId, topic, eventFilter, recurring, boundDescriptor, arguments);
            return lastScheduledApplication;
        }

        public ApplicationHandle launch(Map arguments) {
            launchCalls++;
            lastLaunchArguments = arguments;
            return null;
        }
    }

    private static final class TestApplicationDescriptor extends ApplicationDescriptor {
        private final boolean launchable;
        private final boolean containerLocked;
        private int lockSpecificCalls;
        private int unlockSpecificCalls;

        private TestApplicationDescriptor(
                String applicationId, boolean containerLocked, boolean launchable) {
            super(applicationId);
            this.containerLocked = containerLocked;
            this.launchable = launchable;
        }

        @Override
        public boolean matchDNChain(String pattern) {
            return false;
        }

        @Override
        protected Map getPropertiesSpecific(String locale) {
            Map properties = new HashMap();
            properties.put(APPLICATION_LOCKED, Boolean.valueOf(containerLocked));
            return properties;
        }

        @Override
        protected ApplicationHandle launchSpecific(Map arguments) throws Exception {
            throw new AssertionError("launchSpecific should not be invoked by this coverage test");
        }

        @Override
        protected boolean isLaunchableSpecific() {
            return launchable;
        }

        @Override
        protected void lockSpecific() {
            lockSpecificCalls++;
        }

        @Override
        protected void unlockSpecific() {
            unlockSpecificCalls++;
        }
    }

    private static final class TestScheduledApplication implements ScheduledApplication {
        private final String scheduleId;
        private final String topic;
        private final String eventFilter;
        private final boolean recurring;
        private final ApplicationDescriptor descriptor;
        private final Map arguments;

        private TestScheduledApplication(
                String scheduleId,
                String topic,
                String eventFilter,
                boolean recurring,
                ApplicationDescriptor descriptor,
                Map arguments) {
            this.scheduleId = scheduleId;
            this.topic = topic;
            this.eventFilter = eventFilter;
            this.recurring = recurring;
            this.descriptor = descriptor;
            this.arguments = arguments;
        }

        @Override
        public String getScheduleId() {
            return scheduleId;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public String getEventFilter() {
            return eventFilter;
        }

        @Override
        public boolean isRecurring() {
            return recurring;
        }

        @Override
        public ApplicationDescriptor getApplicationDescriptor() {
            return descriptor;
        }

        @Override
        public Map getArguments() {
            return arguments;
        }

        @Override
        public void remove() {
        }
    }
}
