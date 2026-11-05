/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's object-graph expression evaluation subsystem for a checkout review page.
 *
 * A storefront evaluates expressions against its page model to select payable orders, project
 * their identifiers for a summary, and update a customer's editable delivery address.
 */
public class CheckoutExpressionEvaluationTest {
    @Test
    @Timeout(60)
    void evaluatesCheckoutSelectionsAndUpdatesNestedFormData() throws OgnlException {
        CheckoutPage page = CheckoutPage.sample();
        Map context = Ognl.createDefaultContext(page);
        context.put("minimumTotal", new BigDecimal("100.00"));

        Object payableOrders = Ognl.getValue(
                Ognl.parseExpression("orders.{? status == 'READY' && total >= #minimumTotal}"),
                context,
                page);
        Object orderIds = Ognl.getValue(
                Ognl.parseExpression("orders.{id}"),
                context,
                page);

        Ognl.setValue("customer.address.city", context, page, "Berlin");
        Object deliveryCity = Ognl.getValue("customer.address.city", context, page);

        assertThat(payableOrders)
                .isInstanceOf(List.class)
                .asList()
                .extracting("id")
                .containsExactly("ORD-1042");
        assertThat(orderIds).asList().containsExactly("ORD-1042", "ORD-1043", "ORD-1044");
        assertThat(deliveryCity).isEqualTo("Berlin");
        assertThat(page.getCustomer().getAddress().getCity()).isEqualTo("Berlin");
    }

    public static final class CheckoutPage {
        private final Customer customer;
        private final List<Order> orders;

        private CheckoutPage(Customer customer, List<Order> orders) {
            this.customer = customer;
            this.orders = orders;
        }

        static CheckoutPage sample() {
            return new CheckoutPage(
                    new Customer(new Address("Paris")),
                    List.of(
                            new Order("ORD-1042", "READY", new BigDecimal("249.90")),
                            new Order("ORD-1043", "READY", new BigDecimal("49.90")),
                            new Order("ORD-1044", "CANCELLED", new BigDecimal("399.00"))));
        }

        public Customer getCustomer() {
            return customer;
        }

        public List<Order> getOrders() {
            return orders;
        }
    }

    public static final class Customer {
        private final Address address;

        private Customer(Address address) {
            this.address = address;
        }

        public Address getAddress() {
            return address;
        }
    }

    public static final class Address {
        private String city;

        private Address(String city) {
            this.city = city;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    public static final class Order {
        private final String id;
        private final String status;
        private final BigDecimal total;

        private Order(String id, String status, BigDecimal total) {
            this.id = id;
            this.status = status;
            this.total = total;
        }

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }
}
