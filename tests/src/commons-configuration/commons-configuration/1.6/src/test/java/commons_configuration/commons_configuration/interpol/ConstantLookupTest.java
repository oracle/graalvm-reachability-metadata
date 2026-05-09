/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration.interpol;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.interpol.ConstantLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConstantLookupTest {
    public static final String LOOKUP_VALUE = "constant lookup value";

    private final ConstantLookup lookup = new ConstantLookup();

    @BeforeEach
    public void clearConstantCache() {
        ConstantLookup.clear();
    }

    @Test
    public void resolvesPublicConstantFieldByFullyQualifiedName() {
        final String variableName = ConstantLookupTest.class.getName() + ".LOOKUP_VALUE";

        final String value = lookup.lookup(variableName);

        assertThat(value).isEqualTo(LOOKUP_VALUE);
    }
}
