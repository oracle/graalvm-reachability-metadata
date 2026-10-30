/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ConstructionContextTest {
    @Test
    void resolvesAConstructorCycleThroughAnInterfaceProxy() {
        Injector injector = CircularServices.newInjector();
        CircularServices.Service service = injector.getInstance(CircularServices.Service.class);
        CircularServices.Peer peer = injector.getInstance(CircularServices.Peer.class);
        assertSame(peer, service.peer());
        assertNotSame(service, peer.service);
        assertSame(peer, peer.service.peer());
        assertSame(service, injector.getInstance(CircularServices.Service.class));
    }
}
