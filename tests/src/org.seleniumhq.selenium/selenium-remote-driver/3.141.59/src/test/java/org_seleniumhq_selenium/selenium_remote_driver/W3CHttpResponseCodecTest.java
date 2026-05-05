/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.W3CHttpResponseCodec;

public class W3CHttpResponseCodecTest {
    @Test
    void createsExceptionForW3CErrorResponse() {
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.setStatus(HTTP_NOT_FOUND);
        httpResponse.setContent("""
                {"value":{"error":"no such element","message":"not found","stacktrace":"trace"}}
                """.getBytes(StandardCharsets.UTF_8));

        Response response = new W3CHttpResponseCodec().decode(httpResponse);

        assertEquals("no such element", response.getState());
        assertTrue(response.getValue() instanceof NoSuchElementException);
    }
}
