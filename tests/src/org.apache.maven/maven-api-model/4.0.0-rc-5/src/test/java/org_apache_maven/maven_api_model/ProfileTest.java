/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.Activation;
import org.apache.maven.api.model.ActivationProperty;
import org.apache.maven.api.model.BuildBase;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Profile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileTest {
    @Test
    void attachesPropertyActivatedProfileToProject() {
        ActivationProperty property = ActivationProperty.newBuilder().name("environment").value("ci").build();
        Activation activation = Activation.newBuilder().property(property).activeByDefault(false).build();
        Profile profile = Profile.newBuilder().id("ci").activation(activation)
                .properties(Map.of("skipTests", "false"))
                .subprojects(List.of("integration-tests"))
                .build(BuildBase.newBuilder().defaultGoal("verify").build()).build();
        Model model = Model.newBuilder().artifactId("application").profiles(List.of(profile)).build();
        Profile configured = model.getProfiles().getFirst();

        assertEquals("ci", configured.getId());
        assertEquals("environment", configured.getActivation().getProperty().getName());
        assertEquals("ci", configured.getActivation().getProperty().getValue());
        assertFalse(configured.getActivation().isActiveByDefault());
        assertEquals(Map.of("skipTests", "false"), configured.getProperties());
        assertEquals(List.of("integration-tests"), configured.getSubprojects());
        assertEquals("verify", configured.getBuild().getDefaultGoal());
        assertEquals(Profile.SOURCE_POM, configured.getSource());
    }

    @Test
    void derivesProfileWithIndependentActivationAndProperties() {
        Profile original = Profile.newBuilder().id("local")
                .activation(Activation.newBuilder().activeByDefault(true).build())
                .properties(Map.of("environment", "local")).build();
        Profile updated = original.with().id("release")
                .activation(original.getActivation().withActiveByDefault(false))
                .properties(Map.of("environment", "release")).build();

        assertEquals("local", original.getId());
        assertEquals("local", original.getProperties().get("environment"));
        assertTrue(original.getActivation().isActiveByDefault());
        assertEquals("release", updated.getId());
        assertEquals("release", updated.getProperties().get("environment"));
        assertFalse(updated.getActivation().isActiveByDefault());
    }
}
