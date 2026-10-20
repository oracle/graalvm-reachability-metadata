/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

final class JaxbTestSupport {
    private static final String NO_OPTIMIZE_PROPERTY =
            "com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize";

    private JaxbTestSupport() {
    }

    static void disableOptimizedAccessors() {
        System.setProperty(NO_OPTIMIZE_PROPERTY, Boolean.TRUE.toString());
    }
}
