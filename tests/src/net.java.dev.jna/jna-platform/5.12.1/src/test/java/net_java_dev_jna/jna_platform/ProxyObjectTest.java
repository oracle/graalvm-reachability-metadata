/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.COM.IDispatch;
import com.sun.jna.platform.win32.COM.util.IRawDispatchHandle;
import com.sun.jna.platform.win32.COM.util.ObjectFactory;

public class ProxyObjectTest {
    @Test
    void proxyInterfaceCanExposeRawDispatchHandle() throws Exception {
        ObjectFactoryTest.disableComInitializationAssertions();
        ObjectFactoryTest.installKernel32Facade();
        ObjectFactory factory = new ObjectFactory();
        ObjectFactoryTest.FakeDispatch dispatch = new ObjectFactoryTest.FakeDispatch();

        try {
            RawDispatchComInterface proxy = factory.createProxy(RawDispatchComInterface.class, dispatch);
            IDispatch rawDispatch = proxy.getRawDispatch();

            assertThat(rawDispatch).isSameAs(dispatch);
        } finally {
            factory.disposeAll();
        }
    }

    public interface RawDispatchComInterface extends IRawDispatchHandle {
    }
}
