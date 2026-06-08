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
