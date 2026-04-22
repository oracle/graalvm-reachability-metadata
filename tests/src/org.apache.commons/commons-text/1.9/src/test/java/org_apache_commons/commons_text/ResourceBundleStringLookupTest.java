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

public class ResourceBundleStringLookupTest {

    @Test
    void resolvesMessageFromNamedResourceBundle() {
        String bundleName = ResourceBundleStringLookupTestMessages.class.getName();

        String resolved = StringLookupFactory.INSTANCE.resourceBundleStringLookup().lookup(bundleName + ":greeting");

        assertThat(resolved).isEqualTo("Hello from bundle");
    }
}
