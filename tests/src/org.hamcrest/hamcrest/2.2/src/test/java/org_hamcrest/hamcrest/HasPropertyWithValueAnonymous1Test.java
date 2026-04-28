/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class HasPropertyWithValueAnonymous1Test {

    @Test
    void propertyMatchersInvokeBeanReadMethods() {
        Address address = new Address("London");
        PersonBean bean = new PersonBean("Ada", address);

        MatcherAssert.assertThat(bean, Matchers.hasProperty("name", Matchers.equalTo("Ada")));
        MatcherAssert.assertThat(bean, Matchers.hasPropertyAtPath("address.city", Matchers.equalTo("London")));
    }

    public static final class PersonBean {
        private final String name;
        private final Address address;

        public PersonBean(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public Address getAddress() {
            return address;
        }
    }

    public static final class Address {
        private final String city;

        public Address(String city) {
            this.city = city;
        }

        public String getCity() {
            return city;
        }
    }
}
