package tomcat;

import org.apache.tomcat.SimpleInstanceManager;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstanceManagerTest {
    @Test
    void constructsComponentsUsingEachPublicOverload() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();
        ContextEnvironment byType = (ContextEnvironment) manager.newInstance(ContextEnvironment.class);
        ContextEnvironment byName = (ContextEnvironment) manager.newInstance(ContextEnvironment.class.getName());
        ContextEnvironment byLoader = (ContextEnvironment) manager.newInstance(
                ContextEnvironment.class.getName(), ContextEnvironment.class.getClassLoader());
        byType.setName("typed");
        byName.setName("named");
        byLoader.setName("loaded");
        assertThat(byType.getName()).isEqualTo("typed");
        assertThat(byName.getName()).isEqualTo("named");
        assertThat(byLoader.getName()).isEqualTo("loaded");
        assertThat(byType).isNotSameAs(byName);
        manager.destroyInstance(byType);
        manager.destroyInstance(byName);
        manager.destroyInstance(byLoader);
    }
}
