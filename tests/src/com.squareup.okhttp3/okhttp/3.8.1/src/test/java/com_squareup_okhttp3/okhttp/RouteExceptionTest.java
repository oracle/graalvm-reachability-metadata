/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.internal.connection.RouteException;
import org.junit.jupiter.api.Test;

public class RouteExceptionTest {
    @Test
    void addConnectExceptionSuppressesPreviousFailure() {
        IOException first = new IOException("first route failed");
        IOException second = new IOException("second route failed");
        RouteException routeException = new RouteException(first);

        routeException.addConnectException(second);

        assertThat(routeException.getLastConnectException()).isSameAs(second);
        assertThat(second.getSuppressed()).containsExactly(first);
    }
}
