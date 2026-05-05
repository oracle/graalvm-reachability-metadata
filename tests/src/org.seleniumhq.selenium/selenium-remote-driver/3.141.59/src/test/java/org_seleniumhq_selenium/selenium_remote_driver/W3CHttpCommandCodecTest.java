/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_ATTRIBUTE;

import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.W3CHttpCommandCodec;

public class W3CHttpCommandCodecTest {
    @Test
    void readsAtomResourceWhenEncodingElementAttributeCommand() {
        Command command = new Command(
                new SessionId("session-1"),
                GET_ELEMENT_ATTRIBUTE,
                ImmutableMap.of("id", "element-1", "name", "href"));

        HttpRequest request = new W3CHttpCommandCodec().encode(command);

        assertTrue(request.getContentString().contains("getAttribute"));
    }
}
