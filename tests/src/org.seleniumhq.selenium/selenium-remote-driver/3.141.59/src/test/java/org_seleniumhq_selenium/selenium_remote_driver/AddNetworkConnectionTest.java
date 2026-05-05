/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.mobile.NetworkConnection;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.mobile.AddNetworkConnection;

public class AddNetworkConnectionTest {
    @Test
    void invokesRemoteNetworkConnectionImplementation() throws Exception {
        InterfaceImplementation implementation = new AddNetworkConnection().getImplementation(true);
        ExecuteMethod executeMethod = (commandName, parameters) -> 6;
        Method method = NetworkConnection.class.getMethod("getNetworkConnection");

        NetworkConnection.ConnectionType connectionType =
                (NetworkConnection.ConnectionType) implementation.invoke(executeMethod, null, method);

        assertNotNull(connectionType);
    }
}
