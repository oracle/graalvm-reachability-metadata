/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractCallSiteTest {
    @Test
    void readsAGroovyObjectPropertyThroughAReusableCallSite() throws Throwable {
        CallSiteArray sites = new CallSiteArray(getClass(), new String[]{"reference"});
        Parcel parcel = new Parcel();
        assertEquals("initial", sites.array[0].callGroovyObjectGetProperty(parcel));
        parcel.reference = "updated";
        assertEquals("updated", sites.array[0].callGroovyObjectGetProperty(parcel));
    }

    public static class Parcel extends GroovyObjectSupport {
        public String reference = "initial";
    }
}
