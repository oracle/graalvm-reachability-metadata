/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_cmpn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.deploymentadmin.spi.DeploymentCustomizerPermission;

public class DeploymentCustomizerPermissionTest {
    private static final String IMPLEMENTATION_PROPERTY = "org.osgi.vendor.deploymentadmin";
    private static final String IMPLEMENTATION_PACKAGE =
            "org_osgi.osgi_cmpn.vendor.deploymentadmin";

    private static String previousImplementationPackage;

    @BeforeAll
    static void setUpVendorImplementation() {
        previousImplementationPackage = System.getProperty(IMPLEMENTATION_PROPERTY);
        System.setProperty(IMPLEMENTATION_PROPERTY, IMPLEMENTATION_PACKAGE);
    }

    @AfterAll
    static void restoreVendorImplementation() {
        if (previousImplementationPackage == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, previousImplementationPackage);
        }
    }

    @BeforeEach
    void resetVendorState() {
        org_osgi.osgi_cmpn.vendor.deploymentadmin.DeploymentCustomizerPermission.reset();
    }

    @Test
    void constructorLoadsVendorPermissionClassFromConfiguredPackage() {
        DeploymentCustomizerPermission permission =
                new DeploymentCustomizerPermission("(name=example.bundle)", "privatearea");

        assertThat(permission.getActions()).isEqualTo("privatearea");
        assertThat(
                        org_osgi.osgi_cmpn.vendor.deploymentadmin.DeploymentCustomizerPermission
                                .constructorCalls)
                .isEqualTo(1);
        assertThat(
                        org_osgi.osgi_cmpn.vendor.deploymentadmin.DeploymentCustomizerPermission
                                .lastCreatedName)
                .isEqualTo("(name=example.bundle)");
        assertThat(
                        org_osgi.osgi_cmpn.vendor.deploymentadmin.DeploymentCustomizerPermission
                                .lastCreatedActions)
                .isEqualTo("privatearea");
    }

    @Test
    void impliesDelegatesToVendorPermissionInstance() {
        DeploymentCustomizerPermission authorizingPermission =
                new DeploymentCustomizerPermission("(name=example.bundle)", "privatearea");
        DeploymentCustomizerPermission requestedPermission =
                new DeploymentCustomizerPermission("(name=example.bundle)", "privatearea");

        boolean implied = authorizingPermission.implies(requestedPermission);

        assertThat(implied).isTrue();
        assertThat(
                        org_osgi.osgi_cmpn.vendor.deploymentadmin.DeploymentCustomizerPermission
                                .impliesCalls)
                .isEqualTo(1);
    }
}
