/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.interpol;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.interpol.ConstantLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConstantLookupTest {
    public static final String LOOKUP_VALUE = "constant lookup value";

    private final ConstantLookup lookup = new ConstantLookup();

    @BeforeEach
    void clearConstantCache() {
        ConstantLookup.clear();
    }

    @Test
    void resolvesPublicConstantFieldByFullyQualifiedName() {
        final String variableName = ConstantLookupTest.class.getName() + ".LOOKUP_VALUE";

        final Object value = lookup.lookup(variableName);

        assertThat(value).isEqualTo(LOOKUP_VALUE);
    }
}
