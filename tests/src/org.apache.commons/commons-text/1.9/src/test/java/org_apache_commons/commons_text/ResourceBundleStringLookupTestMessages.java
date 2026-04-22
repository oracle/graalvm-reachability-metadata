/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_text;

import java.util.ListResourceBundle;

public class ResourceBundleStringLookupTestMessages extends ListResourceBundle {

    @Override
    protected Object[][] getContents() {
        return new Object[][] {
            {"greeting", "Hello from bundle"}
        };
    }
}
