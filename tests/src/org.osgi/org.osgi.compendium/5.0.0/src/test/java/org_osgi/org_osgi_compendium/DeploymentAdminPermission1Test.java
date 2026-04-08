/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.deploymentadmin.DeploymentAdminPermission;

public class DeploymentAdminPermission1Test {
    private static final String IMPLEMENTATION_PROPERTY = "org.osgi.vendor.deploymentadmin";
    private static final String IMPLEMENTATION_PACKAGE =
            "org_osgi.org_osgi_compendium.vendor.deploymentadmin";

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
        org_osgi.org_osgi_compendium.vendor.deploymentadmin.DeploymentAdminPermission.reset();
    }

    @Test
    void constructorLoadsVendorPermissionClassFromConfiguredPackage() {
        DeploymentAdminPermission permission =
                new DeploymentAdminPermission("(name=example.bundle)", "list");

        assertThat(permission.getActions()).isEqualTo("list");
        assertThat(
                        org_osgi.org_osgi_compendium.vendor.deploymentadmin
                                .DeploymentAdminPermission.constructorCalls)
                .isEqualTo(1);
        assertThat(
                        org_osgi.org_osgi_compendium.vendor.deploymentadmin
                                .DeploymentAdminPermission.lastCreatedName)
                .isEqualTo("(name=example.bundle)");
        assertThat(
                        org_osgi.org_osgi_compendium.vendor.deploymentadmin
                                .DeploymentAdminPermission.lastCreatedActions)
                .isEqualTo("list");
    }

    @Test
    void impliesDelegatesToVendorPermissionInstance() {
        DeploymentAdminPermission authorizingPermission =
                new DeploymentAdminPermission("(name=example.bundle)", "list,metadata");
        DeploymentAdminPermission requestedPermission =
                new DeploymentAdminPermission("(name=example.bundle)", "list");

        boolean implied = authorizingPermission.implies(requestedPermission);

        assertThat(implied).isTrue();
        assertThat(
                        org_osgi.org_osgi_compendium.vendor.deploymentadmin
                                .DeploymentAdminPermission.impliesCalls)
                .isEqualTo(1);
    }
}
