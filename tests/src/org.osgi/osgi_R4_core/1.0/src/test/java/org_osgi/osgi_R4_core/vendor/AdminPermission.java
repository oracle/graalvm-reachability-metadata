/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor;

import java.security.Permission;
import java.security.PermissionCollection;
import org.osgi.framework.Bundle;

public final class AdminPermission extends Permission {
    private final String actions;

    public AdminPermission(String filter, String actions) {
        super(filter == null ? "*" : filter);
        this.actions = actions == null ? "*" : actions;
    }

    public AdminPermission(Bundle bundle, String actions) {
        super("(id=" + bundle.getBundleId() + ")");
        this.actions = actions == null ? "*" : actions;
    }

    public boolean implies(Permission permission) {
        return equals(permission);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AdminPermission)) {
            return false;
        }
        AdminPermission permission = (AdminPermission) other;
        return getName().equals(permission.getName()) && actions.equals(permission.actions);
    }

    public int hashCode() {
        return 31 * getName().hashCode() + actions.hashCode();
    }

    public String getActions() {
        return actions;
    }

    public PermissionCollection newPermissionCollection() {
        return null;
    }
}
