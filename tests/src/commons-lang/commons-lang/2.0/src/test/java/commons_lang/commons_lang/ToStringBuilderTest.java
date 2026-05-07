/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ToStringBuilderTest {
    private static final String JAVA_VERSION_PROPERTY = "java.version";
    private static final String COMMONS_LANG_COMPATIBLE_JAVA_VERSION = "1.5";

    @BeforeAll
    public static void initializeToStringBuilder() {
        String javaVersion = System.getProperty(JAVA_VERSION_PROPERTY);
        try {
            // Commons Lang 2.0 parses only legacy Java version strings during SystemUtils initialization.
            System.setProperty(JAVA_VERSION_PROPERTY, COMMONS_LANG_COMPATIBLE_JAVA_VERSION);
            ToStringBuilder.getDefaultStyle();
        } finally {
            restoreJavaVersion(javaVersion);
        }
    }

    @Test
    public void reflectionToStringReadsDeclaredFields() {
        DisplayRecord record = new DisplayRecord("alpha", 7);

        String description = ToStringBuilder.reflectionToString(record);

        assertThat(description).contains("name=alpha", "rank=7");
    }

    private static void restoreJavaVersion(String javaVersion) {
        if (javaVersion == null) {
            System.clearProperty(JAVA_VERSION_PROPERTY);
        } else {
            System.setProperty(JAVA_VERSION_PROPERTY, javaVersion);
        }
    }

    private static final class DisplayRecord {
        private final String name;
        private final Integer rank;

        private DisplayRecord(String name, Integer rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
