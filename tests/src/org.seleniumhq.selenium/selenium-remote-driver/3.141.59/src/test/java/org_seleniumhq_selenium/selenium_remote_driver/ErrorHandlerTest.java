/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.remote.ErrorCodes;
import org.openqa.selenium.remote.ErrorHandler;
import org.openqa.selenium.remote.Response;

public class ErrorHandlerTest {
    @Test
    void rebuildsServerSideThrowableFromResponsePayload() {
        Map<String, Object> frame = new HashMap<>();
        frame.put("className", ErrorHandlerTest.class.getName());
        frame.put("methodName", "rebuildsServerSideThrowableFromResponsePayload");
        frame.put("fileName", "ErrorHandlerTest.java");
        frame.put("lineNumber", 23);

        Map<String, Object> value = new HashMap<>();
        value.put("message", "missing element");
        value.put("class", NoSuchElementException.class.getName());
        value.put("stackTrace", Arrays.asList(frame));

        Response response = new Response();
        response.setStatus(ErrorCodes.NO_SUCH_ELEMENT);
        response.setValue(value);

        NoSuchElementException thrown = assertThrows(
                NoSuchElementException.class,
                () -> new ErrorHandler().throwIfResponseFailed(response, 25));

        assertNotNull(thrown.getCause());
        assertEquals(NoSuchElementException.class, thrown.getCause().getClass());
    }
}
