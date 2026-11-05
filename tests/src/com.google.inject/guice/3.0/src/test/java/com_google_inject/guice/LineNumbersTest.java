/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LineNumbersTest {
    @Test
    void formatsMissingBindingErrorsWithTheRequestingMember() {
        Injector injector = Guice.createInjector();
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> injector.getInstance(Client.class));
        String message = error.getMessage();
        assertTrue(message.contains(MissingService.class.getName()));
        assertTrue(message.contains(Client.class.getName()));
        assertTrue(message.contains("No implementation"));
    }

    interface MissingService {
    }

    static final class Client {
        @Inject
        private Client(MissingService service) {
        }
    }
}
