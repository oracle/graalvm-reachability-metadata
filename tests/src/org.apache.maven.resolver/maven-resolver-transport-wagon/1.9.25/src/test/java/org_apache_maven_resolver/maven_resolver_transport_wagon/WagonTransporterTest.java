/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_transport_wagon;

import java.io.File;
import java.net.URI;
import java.util.Properties;

import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WagonTransporterTest {
    @Test
    void peekConnectsWagonAndAppliesHttpHeaders() throws Exception {
        HeaderAwareWagon wagon = new HeaderAwareWagon();
        RecordingWagonProvider wagonProvider = new RecordingWagonProvider(wagon);
        WagonTransporterFactory factory = new WagonTransporterFactory(wagonProvider, null);
        RemoteRepository repository = new RemoteRepository.Builder("central", "default", "http://repo.example.test/")
                .build();

        Transporter transporter = factory.newInstance(new DefaultRepositorySystemSession(), repository);
        try {
            transporter.peek(new PeekTask(URI.create("artifact.jar")));
        } finally {
            transporter.close();
        }

        assertThat(wagonProvider.lookupHint).isEqualTo("http");
        assertThat(wagon.httpHeaders).containsKey("User-Agent");
        assertThat(wagon.resourceExistsPath).isEqualTo("artifact.jar");
        assertThat(wagonProvider.releasedWagon).isSameAs(wagon);
    }

    private static final class RecordingWagonProvider implements WagonProvider {
        private final Wagon wagon;
        private String lookupHint;
        private Wagon releasedWagon;

        private RecordingWagonProvider(Wagon wagon) {
            this.wagon = wagon;
        }

        @Override
        public Wagon lookup(String roleHint) throws NoTransporterException {
            lookupHint = roleHint;
            return wagon;
        }

        @Override
        public void release(Wagon released) {
            releasedWagon = released;
        }
    }

    public static final class HeaderAwareWagon extends AbstractWagon {
        private Properties httpHeaders;
        private String resourceExistsPath;

        public void setHttpHeaders(Properties httpHeaders) {
            this.httpHeaders = httpHeaders;
        }

        @Override
        protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
            // No network connection is needed for this in-memory wagon.
        }

        @Override
        protected void closeConnection() throws ConnectionException {
            // No network connection is needed for this in-memory wagon.
        }

        @Override
        public boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {
            resourceExistsPath = resourceName;
            return true;
        }

        @Override
        public void get(String resourceName, File destination)
                throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
            throw new UnsupportedOperationException("get is not used by this test");
        }

        @Override
        public boolean getIfNewer(String resourceName, File destination, long timestamp)
                throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
            throw new UnsupportedOperationException("getIfNewer is not used by this test");
        }

        @Override
        public void put(File source, String destination)
                throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
            throw new UnsupportedOperationException("put is not used by this test");
        }
    }
}
