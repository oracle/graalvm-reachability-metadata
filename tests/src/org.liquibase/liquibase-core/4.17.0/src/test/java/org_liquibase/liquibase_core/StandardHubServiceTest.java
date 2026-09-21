/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.hub.core.StandardHubService;
import liquibase.hub.model.HubModel;
import liquibase.hub.model.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardHubServiceTest {

    @Test
    void buildsSearchExpressionFromPopulatedHubModelFields() {
        SearchableHubService service = new SearchableHubService();
        Project project = new Project().setName("inventory");

        String search = service.search(project);

        assertThat(search).isEqualTo("name:\"inventory\"");
    }

    public static final class SearchableHubService extends StandardHubService {
        public String search(HubModel model) {
            return toSearchString(model);
        }
    }
}
