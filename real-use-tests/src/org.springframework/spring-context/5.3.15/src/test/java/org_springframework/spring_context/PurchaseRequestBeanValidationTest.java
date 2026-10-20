/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.groups.Default;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Spring Bean Validation integration for nested purchase requests and checkout-specific constraint groups.
public class PurchaseRequestBeanValidationTest {

    @Test
    @Timeout(60)
    public void acceptsDraftsButRequiresDeliveryDetailsAndValidLinesAtCheckout() {
        try (LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            PurchaseRequest draft = new PurchaseRequest("Ada Lovelace", "", List.of(new PurchaseLine("tea", 2)));

            // Saving a basket does not require a delivery address yet.
            assertFalse(validate(validator, draft).hasErrors());

            BindingResult checkout = validate(validator, draft, Checkout.class);
            assertEquals(1, checkout.getErrorCount());
            assertFieldError(checkout, "shippingAddress", "NotBlank", "Enter a delivery address", "");

            PurchaseRequest invalid = new PurchaseRequest("", "12 Market Street",
                    List.of(new PurchaseLine("tea", 2), new PurchaseLine("", 0)));
            BindingResult invalidCheckout = validate(validator, invalid, Checkout.class);
            assertEquals(3, invalidCheckout.getErrorCount());
            assertFieldError(invalidCheckout, "recipient", "NotBlank", "Enter a recipient", "");
            assertFieldError(invalidCheckout, "lines[1].sku", "NotBlank", "Select a product", "");
            assertFieldError(invalidCheckout, "lines[1].quantity", "Min", "Order at least one item", 0);

            PurchaseRequest ready = new PurchaseRequest("Ada Lovelace", "12 Market Street",
                    List.of(new PurchaseLine("tea", 2), new PurchaseLine("coffee", 1)));
            assertFalse(validate(validator, ready, Checkout.class).hasErrors());

            PurchaseRequest empty = new PurchaseRequest("Ada Lovelace", "12 Market Street", List.of());
            BindingResult emptyCheckout = validate(validator, empty, Checkout.class);
            assertEquals(1, emptyCheckout.getErrorCount());
            assertFieldError(emptyCheckout, "lines", "NotEmpty", "Add an item to the basket", List.of());
        }
    }

    private static BindingResult validate(LocalValidatorFactoryBean validator, PurchaseRequest request,
            Object... groups) {
        BindingResult result = new BeanPropertyBindingResult(request, "purchase");
        validator.validate(request, result, groups);
        return result;
    }

    private static void assertFieldError(BindingResult result, String field, String code, String message,
            Object rejectedValue) {
        FieldError error = result.getFieldError(field);
        assertNotNull(error);
        assertFalse(error.isBindingFailure());
        assertEquals(code, error.getCode());
        assertEquals(message, error.getDefaultMessage());
        assertEquals(rejectedValue, error.getRejectedValue());
    }

    public interface Checkout extends Default {
    }

    public static final class PurchaseRequest {
        @NotBlank(message = "Enter a recipient")
        private final String recipient;

        @NotBlank(groups = Checkout.class, message = "Enter a delivery address")
        private final String shippingAddress;

        @Valid
        @NotEmpty(message = "Add an item to the basket")
        private final List<PurchaseLine> lines;

        public PurchaseRequest(String recipient, String shippingAddress, List<PurchaseLine> lines) {
            this.recipient = recipient;
            this.shippingAddress = shippingAddress;
            this.lines = List.copyOf(lines);
        }

        public String getRecipient() {
            return recipient;
        }

        public String getShippingAddress() {
            return shippingAddress;
        }

        public List<PurchaseLine> getLines() {
            return lines;
        }
    }

    public static final class PurchaseLine {
        @NotBlank(message = "Select a product")
        private final String sku;

        @Min(value = 1, message = "Order at least one item")
        private final int quantity;

        public PurchaseLine(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
