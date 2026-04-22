/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.PermissionCollection;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.AdaptPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundlePermission;
import org.osgi.framework.CapabilityPermission;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.condpermadmin.ConditionInfo;
import org.osgi.service.permissionadmin.PermissionInfo;

public class Org_osgi_coreTest {
    @Test
    void versionParsesCanonicalizesAndOrdersVersions() {
        Version parsed = Version.parseVersion(" 1.2.3.qualifier_1-2 ");
        Version same = new Version(1, 2, 3, "qualifier_1-2");
        Version newer = new Version("1.2.4");

        assertThat(parsed.getMajor()).isEqualTo(1);
        assertThat(parsed.getMinor()).isEqualTo(2);
        assertThat(parsed.getMicro()).isEqualTo(3);
        assertThat(parsed.getQualifier()).isEqualTo("qualifier_1-2");
        assertThat(parsed).isEqualTo(same);
        assertThat(parsed.hashCode()).isEqualTo(same.hashCode());
        assertThat(parsed.toString()).isEqualTo("1.2.3.qualifier_1-2");
        assertThat(parsed.compareTo(newer)).isLessThan(0);
        assertThat(Version.parseVersion(null)).isSameAs(Version.emptyVersion);
        assertThat(Version.parseVersion("   ")).isSameAs(Version.emptyVersion);

        assertThatThrownBy(() -> new Version("1.2.3.bad!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid qualifier");
    }

    @Test
    void filtersMatchMapsDictionariesAndServiceReferences() throws Exception {
        Filter filter = FrameworkUtil.createFilter(
                "(&(|(name=Widget)(name=Gadget))(count>=3)(enabled=true)(tags=beta)(letters=c)(description~=spacedvalue))");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", "Widget");
        properties.put("count", 5);
        properties.put("enabled", true);
        properties.put("tags", List.of("alpha", "beta"));
        properties.put("letters", new char[] {'a', 'b', 'c'});
        properties.put("description", "  Spaced Value  ");

        assertThat(filter.matches(properties)).isTrue();

        Map<String, Object> failingProperties = new LinkedHashMap<>(properties);
        failingProperties.put("count", 2);
        assertThat(filter.matches(failingProperties)).isFalse();

        Filter normalized = FrameworkUtil.createFilter(" (  &(name=Widget)(enabled=true)  ) ");
        Filter canonical = FrameworkUtil.createFilter("(&(name=Widget)(enabled=true))");
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put("Name", "Widget");
        dictionary.put("enabled", true);

        assertThat(normalized).isEqualTo(canonical);
        assertThat(normalized.toString()).isEqualTo(canonical.toString());
        assertThat(normalized.match(dictionary)).isTrue();
        assertThat(normalized.matchCase(dictionary)).isFalse();

        TestBundle bundle = new TestBundle(7L, "com.example.bundle", "file:/bundle");
        TestServiceReference reference = new TestServiceReference(
                bundle,
                Map.of(
                        Constants.OBJECTCLASS, new String[] {"com.example.WidgetService", "com.example.AuditService"},
                        "mode", "async"));
        Filter serviceFilter = FrameworkUtil.createFilter(
                "(&(objectClass=com.example.WidgetService)(mode=async))");

        assertThat(serviceFilter.match(reference)).isTrue();
    }

    @Test
    void frameworkUtilitiesReportInvalidSyntaxAndMatchDistinguishedNames() {
        InvalidSyntaxException exception = catchThrowableOfType(
                () -> FrameworkUtil.createFilter("(&(name=Widget)"),
                InvalidSyntaxException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFilter()).isEqualTo("(&(name=Widget)");

        assertThat(FrameworkUtil.matchDistinguishedNameChain(
                        "*, o=ACME, c=US",
                        List.of("cn=Bugs Bunny, o=ACME, c=US")))
                .isTrue();
        assertThat(FrameworkUtil.matchDistinguishedNameChain(
                        "cn=*,o=ACME,c=*",
                        List.of("cn=Daffy Duck, o=ACME, c=NL")))
                .isTrue();
        assertThat(FrameworkUtil.matchDistinguishedNameChain(
                        "*,o=Tweety Inc.,c=US",
                        List.of("ou=S & V, o=Tweety Inc., c=US")))
                .isTrue();
        assertThat(FrameworkUtil.matchDistinguishedNameChain(
                        "*, o=ACME, c=FR",
                        List.of("cn=Bugs Bunny, o=ACME, c=US")))
                .isFalse();
    }

    @Test
    void permissionInfoAndConditionInfoRoundTripEscapedValues() {
        PermissionInfo permissionInfo = new PermissionInfo(
                "java.io.FilePermission",
                "line1\n\"quoted\"\\path",
                "read,write");
        PermissionInfo parsedPermissionInfo = new PermissionInfo("  " + permissionInfo.getEncoded() + "  ");

        assertThat(parsedPermissionInfo).isEqualTo(permissionInfo);
        assertThat(parsedPermissionInfo.getType()).isEqualTo("java.io.FilePermission");
        assertThat(parsedPermissionInfo.getName()).isEqualTo("line1\n\"quoted\"\\path");
        assertThat(parsedPermissionInfo.getActions()).isEqualTo("read,write");
        assertThat(parsedPermissionInfo.toString()).isEqualTo(permissionInfo.getEncoded());

        ConditionInfo conditionInfo = new ConditionInfo(
                "com.example.Condition",
                new String[] {"alpha", "line1\nline2", "\"quoted\"\\value"});
        ConditionInfo parsedConditionInfo = new ConditionInfo("  " + conditionInfo.getEncoded() + "  ");
        String[] args = parsedConditionInfo.getArgs();
        args[0] = "changed";

        assertThat(parsedConditionInfo).isEqualTo(conditionInfo);
        assertThat(parsedConditionInfo.getType()).isEqualTo("com.example.Condition");
        assertThat(parsedConditionInfo.getArgs())
                .containsExactly("alpha", "line1\nline2", "\"quoted\"\\value");
        assertThat(parsedConditionInfo.toString()).isEqualTo(conditionInfo.getEncoded());
    }

    @Test
    void bundleAndServiceExceptionsRetainTypesAndCauses() {
        IllegalStateException cause = new IllegalStateException("boom");
        BundleException bundleException = new BundleException(
                "bundle failed",
                BundleException.REJECTED_BY_HOOK,
                cause);
        ServiceException serviceException = new ServiceException(
                "service failed",
                ServiceException.REMOTE,
                cause);

        assertThat(bundleException.getType()).isEqualTo(BundleException.REJECTED_BY_HOOK);
        assertThat(bundleException.getCause()).isSameAs(cause);
        assertThat(bundleException.getNestedException()).isSameAs(cause);
        assertThat(serviceException.getType()).isEqualTo(ServiceException.REMOTE);
        assertThat(serviceException.getCause()).isSameAs(cause);
    }

    @Test
    void eventsExposeTheirBundlesOriginsReferencesAndTypes() {
        TestBundle origin = new TestBundle(1L, "com.example.origin", "file:/origin");
        TestBundle bundle = new TestBundle(2L, "com.example.target", "file:/target");
        IllegalArgumentException cause = new IllegalArgumentException("warning");
        TestServiceReference reference = new TestServiceReference(
                bundle,
                Map.of(Constants.OBJECTCLASS, new String[] {"com.example.WidgetService"}, Constants.SERVICE_ID, 9L));

        BundleEvent bundleEvent = new BundleEvent(BundleEvent.INSTALLED, bundle, origin);
        FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.WARNING, bundle, cause);
        ServiceEvent serviceEvent = new ServiceEvent(ServiceEvent.MODIFIED, reference);

        assertThat(bundleEvent.getBundle()).isSameAs(bundle);
        assertThat(bundleEvent.getOrigin()).isSameAs(origin);
        assertThat(bundleEvent.getType()).isEqualTo(BundleEvent.INSTALLED);
        assertThat(frameworkEvent.getBundle()).isSameAs(bundle);
        assertThat(frameworkEvent.getThrowable()).isSameAs(cause);
        assertThat(frameworkEvent.getType()).isEqualTo(FrameworkEvent.WARNING);
        assertThat(serviceEvent.getServiceReference()).isSameAs(reference);
        assertThat(serviceEvent.getType()).isEqualTo(ServiceEvent.MODIFIED);
    }

    @Test
    void bundleAndServicePermissionsCanonicalizeActionsAndSupportCollections() {
        BundlePermission bundlePermission = new BundlePermission("com.example.*", "fragment,provide");
        PermissionCollection bundlePermissions = bundlePermission.newPermissionCollection();
        bundlePermissions.add(bundlePermission);
        bundlePermissions.add(new BundlePermission("com.example.host", "host"));

        assertThat(bundlePermission.getActions()).isEqualTo("provide,require,fragment");
        assertThat(bundlePermission.implies(new BundlePermission("com.example.module", "require"))).isTrue();
        assertThat(bundlePermissions.implies(new BundlePermission("com.example.host", "host"))).isTrue();
        assertThat(bundlePermissions.implies(new BundlePermission("org.example.module", "require"))).isFalse();

        ServicePermission servicePermission = new ServicePermission("com.example.*", "register,get");
        PermissionCollection servicePermissions = servicePermission.newPermissionCollection();
        servicePermissions.add(servicePermission);

        assertThat(servicePermission.getActions()).isEqualTo("get,register");
        assertThat(servicePermission.implies(new ServicePermission("com.example.WidgetService", "get"))).isTrue();
        assertThat(servicePermissions.implies(new ServicePermission("com.example.WidgetService", "register"))).isTrue();
        assertThat(servicePermissions.implies(new ServicePermission("org.example.WidgetService", "get"))).isFalse();
    }

    @Test
    void capabilityAndAdaptPermissionsMatchRequestedObjects() {
        TestBundle bundle = new TestBundle(7L, "com.example.bundle", "file:/bundle");

        CapabilityPermission grantedCapabilityPermission = new CapabilityPermission(
                "(&(capability.namespace=osgi.service)(vendor=acme)(name=com.example.bundle)(id=7)(location=file:/bundle))",
                "require");
        CapabilityPermission requestedCapabilityPermission = new CapabilityPermission(
                "osgi.service",
                Map.of("vendor", "acme", "mode", "sync"),
                bundle,
                "require");
        PermissionCollection capabilityPermissions = grantedCapabilityPermission.newPermissionCollection();
        capabilityPermissions.add(grantedCapabilityPermission);
        capabilityPermissions.add(new CapabilityPermission("osgi.service", "provide"));

        assertThat(grantedCapabilityPermission.getActions()).isEqualTo("require");
        assertThat(grantedCapabilityPermission.implies(requestedCapabilityPermission)).isTrue();
        assertThat(capabilityPermissions.implies(new CapabilityPermission("osgi.service", "provide"))).isTrue();

        AdaptPermission grantedAdaptPermission = new AdaptPermission(
                "(&(adaptClass=org.example.Adapter)(name=com.example.bundle)(location=file:/bundle))",
                "adapt");
        AdaptPermission requestedAdaptPermission = new AdaptPermission(
                "org.example.Adapter",
                bundle,
                "adapt");
        PermissionCollection adaptPermissions = grantedAdaptPermission.newPermissionCollection();
        adaptPermissions.add(grantedAdaptPermission);

        assertThat(grantedAdaptPermission.getActions()).isEqualTo("adapt");
        assertThat(grantedAdaptPermission.implies(requestedAdaptPermission)).isTrue();
        assertThat(adaptPermissions.implies(requestedAdaptPermission)).isTrue();
        assertThat(grantedAdaptPermission.implies(new AdaptPermission("org.example.OtherAdapter", bundle, "adapt")))
                .isFalse();
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final String symbolicName;
        private final String location;

        private TestBundle(long bundleId, String symbolicName, String location) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
            this.location = location;
        }

        @Override
        public int getState() {
            return Bundle.ACTIVE;
        }

        @Override
        public void start(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uninstall() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            return new Hashtable<>();
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getLocation() {
            return location;
        }

        @Override
        public ServiceReference[] getRegisteredServices() {
            return null;
        }

        @Override
        public ServiceReference[] getServicesInUse() {
            return null;
        }

        @Override
        public boolean hasPermission(Object permission) {
            return true;
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Dictionary<String, String> getHeaders(String locale) {
            return new Hashtable<>();
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
            return Collections.emptyEnumeration();
        }

        @Override
        public URL getEntry(String path) {
            return null;
        }

        @Override
        public long getLastModified() {
            return 0L;
        }

        @Override
        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            return Collections.emptyEnumeration();
        }

        @Override
        public BundleContext getBundleContext() {
            return null;
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            return Map.of();
        }

        @Override
        public Version getVersion() {
            return Version.emptyVersion;
        }

        @Override
        public Object adapt(Class type) {
            return null;
        }

        @Override
        public File getDataFile(String filename) {
            return null;
        }

        @Override
        public int compareTo(Object other) {
            return Long.compare(bundleId, ((Bundle) other).getBundleId());
        }
    }

    private static final class TestServiceReference implements ServiceReference {
        private final Bundle bundle;
        private final Map<String, Object> properties;

        private TestServiceReference(Bundle bundle, Map<String, Object> properties) {
            this.bundle = bundle;
            this.properties = new LinkedHashMap<>(properties);
            if (!this.properties.containsKey(Constants.SERVICE_ID)) {
                this.properties.put(Constants.SERVICE_ID, 1L);
            }
        }

        @Override
        public Object getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public String[] getPropertyKeys() {
            Collection<String> keys = properties.keySet();
            return keys.toArray(new String[0]);
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return new Bundle[0];
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return true;
        }

        @Override
        public int compareTo(Object other) {
            if (!(other instanceof ServiceReference)) {
                return 1;
            }
            Object thisId = getProperty(Constants.SERVICE_ID);
            Object otherId = ((ServiceReference) other).getProperty(Constants.SERVICE_ID);
            return Long.compare(((Number) thisId).longValue(), ((Number) otherId).longValue());
        }
    }
}
