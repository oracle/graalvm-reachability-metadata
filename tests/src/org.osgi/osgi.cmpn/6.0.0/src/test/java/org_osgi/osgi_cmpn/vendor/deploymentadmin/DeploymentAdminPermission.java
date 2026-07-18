/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_cmpn.vendor.deploymentadmin;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class DeploymentAdminPermission extends Permission {
    public static int constructorCalls;
    public static int impliesCalls;
    public static String lastCreatedName;
    public static String lastCreatedActions;

    private final Set<String> actionSet;
    private final String actions;

    public static void reset() {
        constructorCalls = 0;
        impliesCalls = 0;
        lastCreatedName = null;
        lastCreatedActions = null;
    }

    public DeploymentAdminPermission(String name, String actions) {
        super(name);
        constructorCalls++;
        lastCreatedName = name;
        lastCreatedActions = normalizeActions(actions);
        this.actionSet = parseActions(actions);
        this.actions = String.join(",", this.actionSet);
    }

    @Override
    public boolean implies(Permission permission) {
        impliesCalls++;
        if (!(permission instanceof DeploymentAdminPermission other)) {
            return false;
        }
        return Objects.equals(getName(), other.getName()) && actionSet.containsAll(other.actionSet);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeploymentAdminPermission other)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(actionSet, other.actionSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), actionSet);
    }

    @Override
    public String getActions() {
        return actions;
    }

    @Override
    public PermissionCollection newPermissionCollection() {
        return new Permissions();
    }

    private static Set<String> parseActions(String actions) {
        LinkedHashSet<String> normalizedActions = new LinkedHashSet<String>();
        for (String action : normalizeActions(actions).split(",")) {
            if (!action.isEmpty()) {
                normalizedActions.add(action);
            }
        }
        return normalizedActions;
    }

    private static String normalizeActions(String actions) {
        return actions.replace(" ", "");
    }
}
