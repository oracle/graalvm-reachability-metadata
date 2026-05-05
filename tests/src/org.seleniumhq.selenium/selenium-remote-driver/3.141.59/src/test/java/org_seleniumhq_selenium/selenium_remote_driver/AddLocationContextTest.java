/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.html5.LocationContext;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.html5.AddLocationContext;

public class AddLocationContextTest {
    @Test
    void invokesRemoteLocationContextImplementation() throws Exception {
        Map<String, Number> coordinates = new HashMap<>();
        coordinates.put("latitude", 12.5d);
        coordinates.put("longitude", 45.5d);
        coordinates.put("altitude", 100.0d);
        ExecuteMethod executeMethod = (commandName, parameters) -> coordinates;
        InterfaceImplementation implementation = new AddLocationContext().getImplementation(true);
        Method method = LocationContext.class.getMethod("location");

        Location location = (Location) implementation.invoke(executeMethod, null, method);

        assertEquals(12.5d, location.getLatitude());
        assertEquals(45.5d, location.getLongitude());
        assertEquals(100.0d, location.getAltitude());
    }
}
