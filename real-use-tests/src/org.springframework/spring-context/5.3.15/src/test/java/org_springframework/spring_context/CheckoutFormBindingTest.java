/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Spring data binding and validation for checkout forms with server-controlled payment state.
public class CheckoutFormBindingTest {

    @Test
    @Timeout(60)
    public void acceptsCheckoutDetailsWithoutAllowingPaymentStateChanges() {
        CheckoutForm form = new CheckoutForm();
        DataBinder binder = checkoutBinder(form);
        binder.bind(new MutablePropertyValues(Map.of(
                "recipient", "Ada Lovelace",
                "quantity", "3",
                "giftWrap", "true",
                "paymentStatus", "paid")));
        binder.validate();

        BindingResult result = binder.getBindingResult();
        assertFalse(result.hasErrors());
        assertEquals("Ada Lovelace", form.getRecipient());
        assertEquals(3, form.getQuantity());
        assertTrue(form.isGiftWrap());
        assertEquals("pending", form.getPaymentStatus());
        assertArrayEquals(new String[]{"paymentStatus"}, result.getSuppressedFields());
    }

    @Test
    @Timeout(60)
    public void returnsFieldErrorsAndPreservesRejectedInputForRedisplay() {
        CheckoutForm form = new CheckoutForm();
        DataBinder binder = checkoutBinder(form);
        binder.bind(new MutablePropertyValues(Map.of(
                "recipient", "   ",
                "quantity", "several")));
        binder.validate();

        BindingResult result = binder.getBindingResult();
        assertEquals(2, result.getErrorCount());
        FieldError quantityError = result.getFieldError("quantity");
        assertNotNull(quantityError);
        assertTrue(quantityError.isBindingFailure());
        assertEquals("typeMismatch", quantityError.getCode());
        assertEquals("several", result.getFieldValue("quantity"));
        FieldError recipientError = result.getFieldError("recipient");
        assertNotNull(recipientError);
        assertFalse(recipientError.isBindingFailure());
        assertEquals("recipient.required", recipientError.getCode());
        assertEquals("Enter the recipient's name", recipientError.getDefaultMessage());
        assertEquals("pending", form.getPaymentStatus());
    }

    private static DataBinder checkoutBinder(CheckoutForm form) {
        DataBinder binder = new DataBinder(form, "checkout");
        binder.setAllowedFields("recipient", "quantity", "giftWrap");
        binder.setValidator(new CheckoutValidator());
        return binder;
    }

    public static final class CheckoutValidator implements Validator {
        @Override
        public boolean supports(Class<?> type) {
            return CheckoutForm.class.isAssignableFrom(type);
        }

        @Override
        public void validate(Object target, Errors errors) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "recipient",
                    "recipient.required", "Enter the recipient's name");
            CheckoutForm form = (CheckoutForm) target;
            if (!errors.hasFieldErrors("quantity") && form.getQuantity() < 1) {
                errors.rejectValue("quantity", "quantity.minimum", "Order at least one item");
            }
        }
    }

    public static final class CheckoutForm {
        private String recipient;
        private int quantity;
        private boolean giftWrap;
        private String paymentStatus = "pending";

        public String getRecipient() {
            return recipient;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public boolean isGiftWrap() {
            return giftWrap;
        }

        public void setGiftWrap(boolean giftWrap) {
            this.giftWrap = giftWrap;
        }

        public String getPaymentStatus() {
            return paymentStatus;
        }

        public void setPaymentStatus(String paymentStatus) {
            this.paymentStatus = paymentStatus;
        }
    }
}
