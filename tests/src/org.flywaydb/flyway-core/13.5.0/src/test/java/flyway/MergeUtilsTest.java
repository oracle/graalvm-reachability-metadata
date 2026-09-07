/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import org.flywaydb.core.internal.util.MergeUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MergeUtilsTest {

    @Test
    void copyModelCopiesDeclaredAndInheritedFields() {
        SourceModel source = new SourceModel("source-name", "source-description");
        SourceModel target = new SourceModel("target-name", "target-description");

        MergeUtils.copyModel(source, target);

        assertThat(target.getName()).isEqualTo("source-name");
        assertThat(target.getDescription()).isEqualTo("source-description");
    }

    private static class BaseModel {
        private String description;

        BaseModel(final String description) {
            this.description = description;
        }

        String getDescription() {
            return description;
        }
    }

    private static final class SourceModel extends BaseModel {
        private String name;

        SourceModel(final String name, final String description) {
            super(description);
            this.name = name;
        }

        String getName() {
            return name;
        }
    }
}
