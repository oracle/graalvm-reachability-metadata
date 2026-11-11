/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.DataBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Spring_contextTest {
    @Test
    void bindsFormattedDateThroughXmlConfiguredConversionService() {
        try (GenericXmlApplicationContext context = new GenericXmlApplicationContext(
                "classpath:org_springframework/spring_context/messages.xml")) {
            Delivery delivery = new Delivery();
            DataBinder binder = new DataBinder(delivery);
            binder.setConversionService(context.getBean(ConversionService.class));
            MutablePropertyValues values = new MutablePropertyValues();
            values.add("date", "2026-04-05");
            binder.bind(values);
            assertFalse(binder.getBindingResult().hasErrors());
            assertEquals(LocalDate.of(2026, 4, 5), delivery.getDate());
        }
    }

    public static class Delivery {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }
}
