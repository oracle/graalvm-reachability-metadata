/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_damnhandy.handy_uri_templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.damnhandy.uri.template.UriTemplate;
import org.junit.jupiter.api.Test;

public class DefaultVarExploderTest {
    @Test
    void expandsPropertiesFromAnObject() throws Exception {
        Person person = new Person("Ada", "Paris");

        String expanded = UriTemplate.fromTemplate("{?person*}").set("person", person).expand();

        assertThat(expanded).isEqualTo("?city=Paris&firstName=Ada");
    }

    public static class Person {
        private final String firstName;
        private final String city;

        Person(String firstName, String city) {
            this.firstName = firstName;
            this.city = city;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getCity() {
            return city;
        }
    }
}
