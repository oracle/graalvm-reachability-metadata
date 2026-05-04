/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_parsson.parsson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import jakarta.json.JsonException;
import org.eclipse.parsson.JsonProviderImpl;
import org.junit.jupiter.api.Test;

public class JsonMessagesTest {
    @Test
    void invalidPointerFormatsMessageFromResourceBundle() {
        JsonProviderImpl provider = new JsonProviderImpl();

        JsonException exception = catchThrowableOfType(
                () -> provider.createPointer("missing-leading-slash"),
                JsonException.class);

        assertThat(exception).isNotNull();
        assertThat(exception).hasMessage("A non-empty JSON Pointer must begin with a '/'");
    }
}
