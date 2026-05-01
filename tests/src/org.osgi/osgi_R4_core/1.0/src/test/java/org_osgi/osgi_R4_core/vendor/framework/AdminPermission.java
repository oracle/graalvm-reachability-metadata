/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor.framework;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.osgi.framework.Bundle;

public final class AdminPermission extends Permission {
    public static int stringConstructorCalls;
    public static int bundleConstructorCalls;
    public static String lastCreatedName;
    public static String lastCreatedActions;
    public static long lastCreatedBundleId;

    private final Set<String> actionSet;
    private final String actions;

    public static void reset() {
        stringConstructorCalls = 0;
        bundleConstructorCalls = 0;
        lastCreatedName = null;
        lastCreatedActions = null;
        lastCreatedBundleId = -1L;
    }

    public AdminPermission(String name, String actions) {
        super(name == null ? "*" : name);
        stringConstructorCalls++;
        lastCreatedName = name;
        lastCreatedActions = normalizeActions(actions);
        this.actionSet = parseActions(actions);
        this.actions = String.join(",", this.actionSet);
    }

    public AdminPermission(Bundle bundle, String actions) {
        super("(id=" + bundle.getBundleId() + ")");
        bundleConstructorCalls++;
        lastCreatedName = getName();
        lastCreatedActions = normalizeActions(actions);
        lastCreatedBundleId = bundle.getBundleId();
        this.actionSet = parseActions(actions);
        this.actions = String.join(",", this.actionSet);
    }

    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof AdminPermission)) {
            return false;
        }
        AdminPermission other = (AdminPermission) permission;
        boolean sameBundle = Objects.equals(getName(), other.getName()) || "*".equals(getName());
        return sameBundle && actionSet.containsAll(other.actionSet);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AdminPermission)) {
            return false;
        }
        AdminPermission other = (AdminPermission) obj;
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
        if (actions == null) {
            return "*";
        }
        return actions.replace(" ", "");
    }
}
