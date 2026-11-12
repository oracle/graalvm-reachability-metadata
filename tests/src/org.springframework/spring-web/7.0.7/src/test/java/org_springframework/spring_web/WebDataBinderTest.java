/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import org.junit.jupiter.api.Test;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class WebDataBinderTest {

    @Test
    void fieldMarkerResetsArrayPropertyToEmptyArray() {
        Form form = new Form();
        WebDataBinder binder = new WebDataBinder(form);
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("_tags", "visible");

        binder.bind(values);

        assertThat(form.getTags()).isEmpty();
    }

    @Test
    void fieldMarkerClearsSelectedHttpMethods() {
        MethodForm form = new MethodForm();
        WebDataBinder binder = new WebDataBinder(form);
        binder.bind(new MutablePropertyValues().add("_methods", "visible"));

        assertThat(binder.getBindingResult().hasErrors()).isFalse();
        assertThat(form.getMethods()).isEmpty();
    }

    public static final class MethodForm {
        private RequestMethod[] methods = {RequestMethod.GET, RequestMethod.POST};

        public RequestMethod[] getMethods() {
            return this.methods;
        }

        public void setMethods(RequestMethod[] methods) {
            this.methods = methods;
        }
    }

    public static final class Form {
        private String[] tags = {"spring"};

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }
    }
}
