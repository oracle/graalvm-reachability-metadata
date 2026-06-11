/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.MockedConstructionImpl;
import org.mockito.plugins.MemberAccessor;
import org.mockito.plugins.MockMaker;

public class InstrumentationMemberAccessorTest {
    @Mock private GreetingGateway greetingGateway;

    @Mock private AuditGateway auditGateway;

    @InjectMocks private SetterInjectedGreetingController controller;

    @Test
    void annotationsInstantiateClassAndInjectInterfaceMocksThroughSetters() throws Exception {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
            Mockito.when(greetingGateway.greetingFor("Mockito")).thenReturn("Hello Mockito");

            String greeting = controller.greet("Mockito");

            assertThat(greeting).isEqualTo("constructed:Hello Mockito");
            assertThat(controller.greetingGateway()).isSameAs(greetingGateway);
            assertThat(controller.auditGateway()).isSameAs(auditGateway);
            Mockito.verify(auditGateway).record("Mockito");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultMemberAccessorCreatesInstancesAndAccessesMembers() throws Exception {
        MemberAccessor accessor =
                Mockito.framework().getPlugins().getDefaultPlugin(MemberAccessor.class);
        MockMaker.ConstructionMockControl<Object> control =
                Mockito.mock(MockMaker.ConstructionMockControl.class);
        Constructor<MockedConstructionImpl> constructor =
                MockedConstructionImpl.class.getDeclaredConstructor(
                        MockMaker.ConstructionMockControl.class);
        Field closed = MockedConstructionImpl.class.getDeclaredField("closed");
        Method isClosed = MockedConstructionImpl.class.getDeclaredMethod("isClosed");

        MockedConstructionImpl construction =
                (MockedConstructionImpl) accessor.newInstance(constructor, control);
        assertThat(accessor.get(closed, construction)).isEqualTo(false);

        accessor.set(closed, construction, true);

        assertThat(accessor.invoke(isClosed, construction)).isEqualTo(true);
        assertThat(accessor.get(closed, construction)).isEqualTo(true);
    }
}

interface GreetingGateway {
    String greetingFor(String name);
}

interface AuditGateway {
    void record(String name);
}

class SetterInjectedGreetingController {
    private final String constructionMarker;
    private GreetingGateway greetingGateway;
    private AuditGateway auditGateway;

    private SetterInjectedGreetingController() {
        this.constructionMarker = "constructed";
    }

    public void setGreetingGateway(GreetingGateway greetingGateway) {
        this.greetingGateway = greetingGateway;
    }

    public void setAuditGateway(AuditGateway auditGateway) {
        this.auditGateway = auditGateway;
    }

    String greet(String name) {
        auditGateway.record(name);
        return constructionMarker + ":" + greetingGateway.greetingFor(name);
    }

    GreetingGateway greetingGateway() {
        return greetingGateway;
    }

    AuditGateway auditGateway() {
        return auditGateway;
    }
}
