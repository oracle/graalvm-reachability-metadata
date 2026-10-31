/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jetty.websocket.api.util.WSURI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WSURITest {
    @Test
    void convertsSecureAndPlainUrisWithoutChangingEscapedComponents() throws Exception {
        URI secure = URI.create("https://example.invalid:8443/chat/a%20b?room=one%2Ftwo#section");
        URI websocket = WSURI.toWebsocket(secure);
        assertEquals(URI.create("wss://example.invalid:8443/chat/a%20b?room=one%2Ftwo#section"), websocket);
        assertEquals(secure, WSURI.toHttp(websocket));

        URI plain = URI.create("http://example.invalid:8080/chat?room=blue");
        assertEquals(URI.create("ws://example.invalid:8080/chat?room=blue"), WSURI.toWebsocket(plain));
        assertEquals(plain, WSURI.toHttp(WSURI.toWebsocket(plain)));
    }

    @Test
    void acceptsTextUrlsAndAnOptionalQuery() throws Exception {
        assertEquals(URI.create("wss://example.invalid/chat?room=blue%20sky"),
                WSURI.toWebsocket("https://example.invalid/chat", "room=blue%20sky"));
        assertEquals(URI.create("ws://example.invalid/chat"),
                WSURI.toWebsocket("http://example.invalid/chat", null));
        assertEquals(URI.create("wss://example.invalid/chat"),
                WSURI.toWebsocket(new StringBuilder("https://example.invalid/chat")));
    }

    @Test
    void preservesAlreadyConvertedUris() throws Exception {
        URI websocket = URI.create("wss://example.invalid/chat");
        URI http = URI.create("https://example.invalid/chat");
        assertSame(websocket, WSURI.toWebsocket(websocket));
        assertSame(http, WSURI.toHttp(http));
    }

    @Test
    void rejectsNonWebSocketAndNonHttpSchemes() {
        assertThrows(URISyntaxException.class,
                () -> WSURI.toWebsocket(URI.create("ftp://example.invalid/chat")));
        assertThrows(URISyntaxException.class,
                () -> WSURI.toWebsocket(URI.create("/relative/chat")));
        assertThrows(URISyntaxException.class,
                () -> WSURI.toHttp(URI.create("mailto:user@example.invalid")));
    }
}
