/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PlainObjectMetaMethodSiteTest {
    @Test
    void dispatchesAndCachesAnOrdinaryJavaMethod() throws Throwable {
        CallSiteArray sites = new CallSiteArray(getClass(), new String[]{"label"});
        Labels labels = new Labels();
        assertEquals("parcel-12", sites.array[0].call(labels, 12));
        assertEquals("parcel-23", sites.array[0].call(labels, 23));
    }

    @Test
    void propagatesTheOriginalExceptionFromAJavaMethod() {
        CallSiteArray sites = new CallSiteArray(getClass(), new String[]{"label"});
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> sites.array[0].call(new Labels(), -1));
        assertEquals("number must be nonnegative", failure.getMessage());
    }

    public static class Labels {
        public String label(int number) {
            if (number < 0) {
                throw new IllegalArgumentException("number must be nonnegative");
            }
            return "parcel-" + number;
        }
    }
}
