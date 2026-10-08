/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

public class ClassInstanceLoaderTest {

    @Test
    void createsAUsableLibraryClassInstance() {
        Metadata metadata = ClassInstanceLoader.getClassInstance(Metadata.class);

        metadata.put("source", "class-instance-loader");
        assertThat(metadata.getString("source")).isEqualTo("class-instance-loader");
    }
}
