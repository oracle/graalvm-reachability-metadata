/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.Map;

import org.eclipse.osgi.internal.baseadaptor.BaseStorageHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseStorageHookTest {
    @Test
    public void expandsEnvironmentVariablesInStorageLocations() {
        Map.Entry<String, String> variable = System.getenv().entrySet().stream()
                .filter(entry -> !entry.getKey().contains("$") && !entry.getKey().isEmpty())
                .findFirst().orElseThrow();
        try (FrameworkProperty property = new FrameworkProperty(variable.getKey(), null)) {
            String input = "prefix/$" + variable.getKey() + "$/suffix";
            assertEquals("prefix/" + variable.getValue() + "/suffix", BaseStorageHook.substituteVars(input));
        }
    }
}
