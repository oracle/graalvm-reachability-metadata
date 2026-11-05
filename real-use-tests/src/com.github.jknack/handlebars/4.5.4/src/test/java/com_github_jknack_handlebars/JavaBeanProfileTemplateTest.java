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
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' JavaBean value-resolution subsystem for a customer profile.
public class JavaBeanProfileTemplateTest {
    @Test
    @Timeout(60)
    public void rendersProfileFromApplicationBeanModel() throws IOException {
        Customer customer = new Customer(
                "CUS-17",
                "Mara & Co.",
                new Address("Berlin", "Germany"),
                true,
                List.of(
                        new Order("ORD-42", "29.95", "Ready"),
                        new Order("ORD-43", "8.50", "Shipped")));
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline("""
                <section data-customer="{{id}}">
                  <h1>{{displayName}}</h1>
                  <address>{{address.city}}, {{address.country}}</address>
                  {{#if preferred}}<strong>Priority customer</strong>{{/if}}
                  <ul>{{#each orders}}<li data-order="{{number}}">{{status}}: ${{total}}</li>{{/each}}</ul>
                </section>
                """);

        Context context = Context.newBuilder(customer)
                .resolver(JavaBeanValueResolver.INSTANCE)
                .build();
        String rendered = template.apply(context);

        assertEquals(
                "<section data-customer=\"CUS-17\"> <h1>Mara &amp; Co.</h1> "
                        + "<address>Berlin, Germany</address> <strong>Priority customer</strong> "
                        + "<ul><li data-order=\"ORD-42\">Ready: $29.95</li>"
                        + "<li data-order=\"ORD-43\">Shipped: $8.50</li></ul> </section>",
                rendered.replaceAll("\\s+", " ").trim());
    }

    public static final class Customer {
        private final String id;
        private final String displayName;
        private final Address address;
        private final boolean preferred;
        private final List<Order> orders;

        public Customer(
                String id,
                String displayName,
                Address address,
                boolean preferred,
                List<Order> orders) {
            this.id = id;
            this.displayName = displayName;
            this.address = address;
            this.preferred = preferred;
            this.orders = orders;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Address getAddress() {
            return address;
        }

        public boolean isPreferred() {
            return preferred;
        }

        public List<Order> getOrders() {
            return orders;
        }
    }

    public static final class Address {
        private final String city;
        private final String country;

        public Address(String city, String country) {
            this.city = city;
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public String getCountry() {
            return country;
        }
    }

    public static final class Order {
        private final String number;
        private final String total;
        private final String status;

        public Order(String number, String total, String status) {
            this.number = number;
            this.total = total;
            this.status = status;
        }

        public String getNumber() {
            return number;
        }

        public String getTotal() {
            return total;
        }

        public String getStatus() {
            return status;
        }
    }
}
