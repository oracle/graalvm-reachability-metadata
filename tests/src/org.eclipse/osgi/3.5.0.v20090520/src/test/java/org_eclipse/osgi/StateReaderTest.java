/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.net.URI;
import java.nio.file.Path;

import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StateReaderTest {
    @Test
    public void restoresTypedAttributesFromPersistedResolverState(@TempDir Path directory) throws Exception {
        StateObjectFactory factory = StateObjectFactory.defaultFactory;
        State original = ResolverFixtures.state();
        original.setTimeStamp(42L);
        factory.writeState(original, directory.toFile());

        State restored = factory.readState(directory.toFile());
        assertNotNull(restored);
        assertEquals(42L, restored.getTimeStamp());
        assertEquals("example.catalog", restored.getBundle(17L).getSymbolicName());
        assertEquals(URI.create("https://example.invalid/catalog"),
                restored.getBundle(17L).getExportPackages()[0].getAttributes().get("endpoint"));
        assertEquals(7L, restored.getBundle(17L).getExportPackages()[0].getAttributes().get("priority"));
    }
}
