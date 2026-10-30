/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import io.sundr.builder.annotations.Buildable;

@Buildable
public class Address {
    private final String city;
    private final String postalCode;

    public Address(String city, String postalCode) {
        this.city = city;
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }
}
