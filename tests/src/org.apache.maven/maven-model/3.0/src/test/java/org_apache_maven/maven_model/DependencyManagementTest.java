/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.DependencyManagement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyManagementTest {
    @Test
    void addsNullDependencyToDependencies() {
        DependencyManagement dependencyManagement = new DependencyManagement();

        dependencyManagement.addDependency(null);

        assertThat(dependencyManagement.getDependencies()).hasSize(1);
        assertThat(dependencyManagement.getDependencies().get(0)).isNull();
    }
}
