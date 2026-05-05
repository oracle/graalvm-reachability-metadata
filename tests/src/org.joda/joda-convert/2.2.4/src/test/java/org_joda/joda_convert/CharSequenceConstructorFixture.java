/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import org.joda.convert.FromString;
import org.joda.convert.ToString;

public class CharSequenceConstructorFixture {
    private final String value;

    @FromString
    public CharSequenceConstructorFixture(CharSequence value) {
        this.value = value.toString();
    }

    @ToString
    public String asText() {
        return value;
    }
}
