/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import static org.junit.jupiter.api.Assertions.*;

public class DataBinderTest {
    @Test
    void bindsAllowedFieldsAndReportsConversionErrors() {
        TestBeans.Person person = new TestBeans.Person();
        DataBinder binder = new DataBinder(person, "person");
        binder.setAllowedFields(new String[]{"name", "age"});
        MutablePropertyValues values = new MutablePropertyValues();
        values.addPropertyValue("name", "Ada");
        values.addPropertyValue("age", "not-a-number");
        values.addPropertyValue("tags", "suppressed");
        binder.bind(values);
        assertEquals("Ada", person.getName());
        assertTrue(binder.getBindingResult().hasFieldErrors("age"));
        assertEquals("typeMismatch", binder.getBindingResult().getFieldError("age").getCode());
        assertArrayEquals(new String[]{"tags"}, binder.getBindingResult().getSuppressedFields());
    }
}
