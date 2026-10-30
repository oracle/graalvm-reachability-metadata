/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.Hashtable;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

final class ResolverFixtures {
    private ResolverFixtures() {
    }

    static State state() throws BundleException {
        StateObjectFactory factory = StateObjectFactory.defaultFactory;
        State state = factory.createState(false);
        Hashtable<String, String> manifest = new Hashtable<>();
        manifest.put(Constants.BUNDLE_MANIFESTVERSION, "2");
        manifest.put(Constants.BUNDLE_SYMBOLICNAME, "example.catalog");
        manifest.put(Constants.EXPORT_PACKAGE,
                "example.catalog.api;endpoint:uri=\"https://example.invalid/catalog\";priority:long=\"7\"");
        BundleDescription bundle = factory.createBundleDescription(state, manifest, "catalog", 17L);
        state.addBundle(bundle);
        return state;
    }
}
