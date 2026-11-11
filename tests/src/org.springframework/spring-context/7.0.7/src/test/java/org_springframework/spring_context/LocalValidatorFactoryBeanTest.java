/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LocalValidatorFactoryBeanTest {
    @Test
    void configuresProviderClassLoaderAndValidatesThroughSpring() {
        try (GenericApplicationContext context = new GenericApplicationContext();
                LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean()) {
            context.refresh();
            validator.setApplicationContext(context);
            validator.setProviderClass(HibernateValidator.class);
            validator.afterPropertiesSet();
            Customer customer = new Customer("");
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(customer, "customer");
            validator.validate(customer, errors);
            assertNotNull(errors.getFieldError("name"));
            assertEquals("NotBlank", errors.getFieldError("name").getCode());
            assertFalse(validator.validate(new Customer("Ada")).iterator().hasNext());
        }
    }

    public record Customer(@NotBlank String name) {
    }
}
