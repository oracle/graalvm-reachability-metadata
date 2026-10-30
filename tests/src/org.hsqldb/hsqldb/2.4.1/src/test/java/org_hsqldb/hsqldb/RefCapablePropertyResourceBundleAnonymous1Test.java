/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.util.Locale;

import org.hsqldb.lib.RefCapablePropertyResourceBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RefCapablePropertyResourceBundleAnonymous1Test {
    @Test
    void readsHelpTextReferencedByAnEmptyProperty() {
        RefCapablePropertyResourceBundle bundle = RefCapablePropertyResourceBundle.getBundle(
                "org.hsqldb.lib.tar.rb", Locale.ROOT, getClass().getClassLoader());
        String syntax = bundle.getString("TarReader.syntax");
        assertTrue(syntax.contains("SYNTAX:"));
        assertTrue(syntax.contains("--directory=path"));
    }
}
