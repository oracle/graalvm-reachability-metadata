/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelTest {
    @Test
    void addsNullContributorToContributors() {
        Model model = new Model();

        model.addContributor(null);

        assertThat(model.getContributors()).hasSize(1);
        assertThat(model.getContributors().get(0)).isNull();
    }
}
