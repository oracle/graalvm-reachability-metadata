/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium.vendor.deploymentadmin;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.Locale;
import java.util.Objects;

public final class DeploymentCustomizerPermission extends Permission {
    public static int constructorCalls;
    public static String lastCreatedName;
    public static String lastCreatedActions;

    private final String actions;

    public static void reset() {
        constructorCalls = 0;
        lastCreatedName = null;
        lastCreatedActions = null;
    }

    public DeploymentCustomizerPermission(String name, String actions) {
        super(name);
        constructorCalls++;
        lastCreatedName = name;
        this.actions = normalizeActions(actions);
        lastCreatedActions = this.actions;
    }

    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof DeploymentCustomizerPermission other)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(actions, other.actions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeploymentCustomizerPermission other)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(actions, other.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), actions);
    }

    @Override
    public String getActions() {
        return actions;
    }

    @Override
    public PermissionCollection newPermissionCollection() {
        return new Permissions();
    }

    private static String normalizeActions(String actions) {
        return actions.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
