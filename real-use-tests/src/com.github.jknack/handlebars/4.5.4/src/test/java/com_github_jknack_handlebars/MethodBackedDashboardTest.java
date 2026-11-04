/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.MethodValueResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' method-based value-resolution subsystem for a customer dashboard.
public class MethodBackedDashboardTest {
    @Test
    @Timeout(60)
    public void rendersDashboardFromApplicationMethods() throws IOException {
        CustomerDashboard dashboard = new CustomerDashboard(
                "CUS-17",
                "Mara & Co.",
                "Priority",
                List.of(
                        new Shipment("SHP-42", "Berlin", "In transit"),
                        new Shipment("SHP-43", "Hamburg", "Delivered")));
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline("""
                <section data-customer="{{customerId}}">
                  <h1>{{displayName}}</h1>
                  <p class="tier">{{tierLabel}}</p>
                  <ul>{{#each shipments}}<li data-shipment="{{trackingCode}}">{{destination}}: {{statusLabel}}</li>{{/each}}</ul>
                </section>
                """);

        Context context = Context.newBuilder(dashboard)
                .resolver(MethodValueResolver.INSTANCE)
                .build();
        String rendered = template.apply(context);

        assertEquals(
                "<section data-customer=\"CUS-17\"> <h1>Mara &amp; Co.</h1> "
                        + "<p class=\"tier\">Priority</p> "
                        + "<ul><li data-shipment=\"SHP-42\">Berlin: In transit</li>"
                        + "<li data-shipment=\"SHP-43\">Hamburg: Delivered</li></ul> </section>",
                rendered.replaceAll("\\s+", " ").trim());
    }

    public static final class CustomerDashboard {
        private final String customerId;
        private final String displayName;
        private final String tierLabel;
        private final List<Shipment> shipments;

        public CustomerDashboard(
                String customerId,
                String displayName,
                String tierLabel,
                List<Shipment> shipments) {
            this.customerId = customerId;
            this.displayName = displayName;
            this.tierLabel = tierLabel;
            this.shipments = shipments;
        }

        public String customerId() {
            return customerId;
        }

        public String displayName() {
            return displayName;
        }

        public String tierLabel() {
            return tierLabel;
        }

        public List<Shipment> shipments() {
            return shipments;
        }
    }

    public static final class Shipment {
        private final String trackingCode;
        private final String destination;
        private final String statusLabel;

        public Shipment(String trackingCode, String destination, String statusLabel) {
            this.trackingCode = trackingCode;
            this.destination = destination;
            this.statusLabel = statusLabel;
        }

        public String trackingCode() {
            return trackingCode;
        }

        public String destination() {
            return destination;
        }

        public String statusLabel() {
            return statusLabel;
        }
    }
}
