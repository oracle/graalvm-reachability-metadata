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
import org.openqa.selenium.html5.LocalStorage;
import org.openqa.selenium.html5.WebStorage;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.html5.AddWebStorage;

public class AddWebStorageTest {
    @Test
    void invokesRemoteWebStorageImplementation() throws Exception {
        InterfaceImplementation implementation = new AddWebStorage().getImplementation(true);
        ExecuteMethod executeMethod = (commandName, parameters) -> null;
        Method method = WebStorage.class.getMethod("getLocalStorage");

        LocalStorage storage = (LocalStorage) implementation.invoke(executeMethod, null, method);

        assertNotNull(storage);
    }
}
