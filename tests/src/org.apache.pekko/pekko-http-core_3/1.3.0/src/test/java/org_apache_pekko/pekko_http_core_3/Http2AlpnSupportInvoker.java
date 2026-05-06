/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_core_3;

import org.apache.pekko.http.impl.engine.http2.Http2AlpnSupport;

import javax.net.ssl.SSLEngine;

final class Http2AlpnSupportInvoker {
    private Http2AlpnSupportInvoker() {
    }

    static void setClientApplicationProtocols(SSLEngine engine) {
        Http2AlpnSupport.clientSetApplicationProtocols(engine, new String[] {"h2"});
    }
}
