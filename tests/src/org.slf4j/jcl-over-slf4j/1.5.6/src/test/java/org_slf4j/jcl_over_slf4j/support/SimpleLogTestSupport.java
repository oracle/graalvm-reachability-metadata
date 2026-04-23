/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j.support;

import org.apache.commons.logging.impl.SimpleLog;

public final class SimpleLogTestSupport {

    private SimpleLogTestSupport() {
    }

    public static SimpleLog newSimpleLog(String name) {
        return new SimpleLog(name);
    }
}
