/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey_jersey_test_framework.jersey_test_framework_core;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JerseyTestTest implements TestContainerFactory {
    private static final String TEST_CONTAINER_FACTORY_PROPERTY_NAME = "jersey.test.containerFactory";

    static int instanceCount;
    static int createCount;
    static URI lastBaseUri;
    static Class<? extends AppDescriptor> lastDescriptorClass;

    public JerseyTestTest() {
        instanceCount++;
    }

    @Test
    void usesConfiguredDefaultTestContainerFactoryClassName() throws Exception {
        reset();
        System.setProperty(TEST_CONTAINER_FACTORY_PROPERTY_NAME, JerseyTestTest.class.getName());

        try {
            LowLevelAppDescriptor descriptor = new LowLevelAppDescriptor.Builder(JerseyTestTest.class).build();
            ExposedJerseyTest test = new ExposedJerseyTest(descriptor);

            assertThat(instanceCount).isEqualTo(1);
            assertThat(createCount).isEqualTo(1);
            assertThat(lastDescriptorClass).isEqualTo(LowLevelAppDescriptor.class);
            assertThat(lastBaseUri).isEqualTo(URI.create("http://localhost:9998/"));
            assertThat(test.exposedTestContainerFactory()).isInstanceOf(JerseyTestTest.class);
        } finally {
            System.clearProperty(TEST_CONTAINER_FACTORY_PROPERTY_NAME);
        }
    }

    @Override
    public Class<? extends AppDescriptor> supports() {
        return LowLevelAppDescriptor.class;
    }

    @Override
    public TestContainer create(URI baseUri, AppDescriptor ad) throws IllegalArgumentException {
        createCount++;
        lastBaseUri = baseUri;
        lastDescriptorClass = ad.getClass();
        return new RecordingTestContainer(baseUri);
    }

    private static void reset() {
        instanceCount = 0;
        createCount = 0;
        lastBaseUri = null;
        lastDescriptorClass = null;
    }
}

final class ExposedJerseyTest extends JerseyTest {
    ExposedJerseyTest(LowLevelAppDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    protected Client getClient(TestContainer tc, AppDescriptor ad) {
        return null;
    }

    TestContainerFactory exposedTestContainerFactory() {
        return getTestContainerFactory();
    }
}

final class RecordingTestContainer implements TestContainer {
    private final URI baseUri;

    RecordingTestContainer(URI baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public Client getClient() {
        return null;
    }

    @Override
    public URI getBaseUri() {
        return baseUri;
    }

    @Override
    public void start() {
        // The test double has no external container resources to start.
    }

    @Override
    public void stop() {
        // The test double has no external container resources to stop.
    }
}
