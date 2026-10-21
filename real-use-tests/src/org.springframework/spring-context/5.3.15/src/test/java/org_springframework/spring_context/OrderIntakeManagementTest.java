/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Spring JMX export to monitor order intake and pause it through an application's management interface.
public class OrderIntakeManagementTest {

    @Test
    @Timeout(60)
    public void exposesLiveMetricsAndAdministrativeControls() throws Exception {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("shop.orders:type=OrderIntake");
        ManagedOrderIntake intake = new ManagedOrderIntake();
        MBeanExporter exporter = new MBeanExporter();
        exporter.setServer(server);
        exporter.setAssembler(new MetadataMBeanInfoAssembler(new AnnotationJmxAttributeSource()));
        exporter.setBeans(Map.of(name.toString(), intake));

        try {
            exporter.afterPropertiesSet();
            exporter.afterSingletonsInstantiated();
            assertTrue(server.isRegistered(name));

            assertEquals("Accepted order-42", intake.accept("order-42"));
            assertEquals("Accepted order-43", intake.accept("order-43"));
            assertEquals(2, server.getAttribute(name, "AcceptedOrders"));
            assertEquals(false, server.getAttribute(name, "Paused"));

            // An operator pauses intake through JMX, not through the application's Java reference.
            server.setAttribute(name, new Attribute("Paused", true));
            IllegalStateException rejection = assertThrows(IllegalStateException.class,
                    () -> intake.accept("order-44"));
            assertEquals("Order intake is paused for maintenance", rejection.getMessage());
            assertEquals(2, server.getAttribute(name, "AcceptedOrders"));

            assertEquals(2, server.invoke(name, "resetStatistics", new Object[0], new String[0]));
            assertEquals(0, server.getAttribute(name, "AcceptedOrders"));
            server.setAttribute(name, new Attribute("Paused", false));

            assertEquals("Accepted order-44", intake.accept("order-44"));
            assertEquals(1, server.getAttribute(name, "AcceptedOrders"));
        } finally {
            exporter.destroy();
        }
        assertFalse(server.isRegistered(name));
    }

    @ManagedResource(description = "Order intake maintenance and throughput")
    public static final class ManagedOrderIntake {
        private int acceptedOrders;
        private boolean paused;

        public synchronized String accept(String orderId) {
            if (paused) {
                throw new IllegalStateException("Order intake is paused for maintenance");
            }
            acceptedOrders++;
            return "Accepted " + orderId;
        }

        @ManagedAttribute(description = "Orders accepted since the last statistics reset")
        public synchronized int getAcceptedOrders() {
            return acceptedOrders;
        }

        @ManagedAttribute(description = "Whether order intake is paused for maintenance")
        public synchronized boolean isPaused() {
            return paused;
        }

        @ManagedAttribute
        public synchronized void setPaused(boolean paused) {
            this.paused = paused;
        }

        @ManagedOperation(description = "Reset the accepted-order counter and return its previous value")
        public synchronized int resetStatistics() {
            int previousCount = acceptedOrders;
            acceptedOrders = 0;
            return previousCount;
        }
    }
}
