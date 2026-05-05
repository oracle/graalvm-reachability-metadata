/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.osgi.framework.Bundle;

public class AdminPermission extends Permission {
    private final String actions;
    private final Set<String> actionSet;

    public AdminPermission(String filter, String actions) {
        super(filter == null ? "*" : filter);
        this.actions = actions == null ? "*" : actions;
        this.actionSet = parseActions(this.actions);
    }

    public AdminPermission(Bundle bundle, String actions) {
        this("(id=" + bundle.getBundleId() + ")", actions);
    }

    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof AdminPermission)) {
            return false;
        }

        AdminPermission requestedPermission = (AdminPermission) permission;
        boolean nameMatches = "*".equals(getName()) || getName().equals(requestedPermission.getName());
        boolean actionMatches = actionSet.contains("*") || actionSet.containsAll(requestedPermission.actionSet);
        return nameMatches && actionMatches;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AdminPermission)) {
            return false;
        }
        AdminPermission that = (AdminPermission) object;
        return getName().equals(that.getName()) && actionSet.equals(that.actionSet);
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
        return null;
    }

    private static Set<String> parseActions(String actions) {
        Set<String> result = new LinkedHashSet<String>();
        Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(action -> !action.isEmpty())
                .forEach(result::add);
        return result;
    }
}
