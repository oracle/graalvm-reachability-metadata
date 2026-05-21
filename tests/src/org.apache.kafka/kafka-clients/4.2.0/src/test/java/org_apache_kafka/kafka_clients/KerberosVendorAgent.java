/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import java.lang.instrument.Instrumentation;

public final class KerberosVendorAgent {

    private KerberosVendorAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.setProperty("java.vendor", "IBM Corporation");
    }
}
