/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts;

import java.io.IOException;

import org.jetbrains.kotlin.org.apache.http.client.HttpRequestRetryHandler;
import org.jetbrains.kotlin.org.apache.http.protocol.HttpContext;

public class CustomWagonRetryHandler implements HttpRequestRetryHandler {
    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        return false;
    }
}
