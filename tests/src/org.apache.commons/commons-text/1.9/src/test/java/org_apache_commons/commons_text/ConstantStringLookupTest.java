/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_text;

import org.apache.commons.text.lookup.StringLookupFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstantStringLookupTest {

    @Test
    void resolvesPublicStaticFieldThroughConstantLookup() {
        StringLookupFactory.clear();

        String resolved = StringLookupFactory.INSTANCE.constantStringLookup().lookup("java.lang.Integer.MAX_VALUE");

        assertThat(resolved).isEqualTo(String.valueOf(Integer.MAX_VALUE));
    }
}
