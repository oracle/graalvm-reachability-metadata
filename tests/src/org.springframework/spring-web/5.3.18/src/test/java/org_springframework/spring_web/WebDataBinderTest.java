/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class WebDataBinderTest {
    @Test
    void createsEmptyArrayValueForArrayPropertyType() {
        ArrayBackedForm form = new ArrayBackedForm();
        WebDataBinder binder = new WebDataBinder(form);

        binder.bind(new MutablePropertyValues(Map.of("_selectedNames", "visible")));

        assertThat(form.getSelectedNames()).isEmpty();
    }

    public static class ArrayBackedForm {
        private String[] selectedNames = {"spring"};

        public String[] getSelectedNames() {
            return selectedNames;
        }

        public void setSelectedNames(String[] selectedNames) {
            this.selectedNames = selectedNames;
        }
    }
}
