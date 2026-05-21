/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class KerberosVendorExtension implements BeforeAllCallback {

    static {
        useIbmVendor();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        useIbmVendor();
    }

    private static void useIbmVendor() {
        System.setProperty("java.vendor", "IBM Corporation");
    }
}
