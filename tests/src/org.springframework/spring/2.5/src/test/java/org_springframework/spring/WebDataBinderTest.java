/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.junit.jupiter.api.Assertions.*;

public class WebDataBinderTest {
    public static class Form {
        private String[] choices = {"selected"};

        public String[] getChoices() {
            return choices;
        }

        public void setChoices(String[] choices) {
            this.choices = choices;
        }
    }

    @Test
    void resetsUnsubmittedArrayFromFieldMarker() {
        Form form = new Form();
        WebDataBinder binder = new WebDataBinder(form);
        MutablePropertyValues values = new MutablePropertyValues();
        values.addPropertyValue("_choices", "visible");
        binder.bind(values);
        assertFalse(binder.getBindingResult().hasErrors());
        assertArrayEquals(new String[0], form.getChoices());
    }
}
