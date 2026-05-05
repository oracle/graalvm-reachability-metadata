/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts;

import org.jetbrains.kotlin.org.apache.http.HttpResponse;
import org.jetbrains.kotlin.org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.jetbrains.kotlin.org.apache.http.protocol.HttpContext;

public class CustomServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {
    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        return false;
    }

    @Override
    public long getRetryInterval() {
        return 0L;
    }
}
