/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdiff_tests;

import java.nio.file.Path;
import java.util.Set;

import jdiff.API;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XMLToAPITest {
    @TempDir
    Path directory;

    @Test
    void readsPackagesAndClassesFromAnApiDescription() throws Exception {
        String members = """
                <doc>A widget with &lt;code&gt;documented&lt;/code&gt; members.</doc>
                <constructor type="void" static="false" final="false"
                             deprecated="not deprecated" visibility="public"/>
                <field name="LIMIT" type="int" value="12" transient="false" volatile="false"
                       static="true" final="true" deprecated="not deprecated" visibility="public"/>
                """ + ApiFiles.method("lookup", "java.lang.String");
        API api = ApiFiles.read(directory, "Catalog",
                ApiFiles.packageWithClass("example.catalog", members)
                        + ApiFiles.packageWithClass("example.tools", ""));

        assertEquals("Catalog", api.name_);
        assertEquals(2, api.packages_.size());
        assertEquals(Set.of("example.catalog.Widget", "example.tools.Widget"), api.classes_.keySet());
        assertNotNull(api.classes_.get("example.catalog.Widget"));
    }

    @Test
    void keepsSeparateModelsWhenReadingSuccessiveFiles() throws Exception {
        API first = ApiFiles.read(directory, "First", ApiFiles.packageWithClass("example.first", ""));
        API second = ApiFiles.read(directory, "Second", ApiFiles.packageWithClass("example.second", ""));

        assertEquals("First", first.name_);
        assertEquals(Set.of("example.first.Widget"), first.classes_.keySet());
        assertEquals("Second", second.name_);
        assertEquals(Set.of("example.second.Widget"), second.classes_.keySet());
    }
}
