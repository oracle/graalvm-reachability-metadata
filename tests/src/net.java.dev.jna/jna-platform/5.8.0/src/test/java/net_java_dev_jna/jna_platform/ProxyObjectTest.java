/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.COM.Dispatch;
import com.sun.jna.platform.win32.COM.IDispatch;
import com.sun.jna.platform.win32.COM.util.IRawDispatchHandle;
import com.sun.jna.platform.win32.COM.util.ObjectFactory;
import com.sun.jna.platform.win32.COM.util.ProxyObject;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class ProxyObjectTest {

    @Test
    void proxyDispatchesRawHandleMethodToProxyObjectImplementation() throws Throwable {
        DispatchReturningProxyObject.rawDispatch = new Dispatch();
        ProxyObject handler = allocateProxyObject();
        IRawDispatchHandle proxy = (IRawDispatchHandle) Proxy.newProxyInstance(
            ProxyObjectTest.class.getClassLoader(),
            new Class<?>[] { IRawDispatchHandle.class },
            handler
        );

        IDispatch result = proxy.getRawDispatch();

        assertThat(result).isSameAs(DispatchReturningProxyObject.rawDispatch);
    }

    private static ProxyObject allocateProxyObject() throws Throwable {
        return (ProxyObject) unsafe().allocateInstance(DispatchReturningProxyObject.class);
    }

    private static Unsafe unsafe() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup());
        VarHandle varHandle = lookup.findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class);
        return (Unsafe) varHandle.get();
    }

    private static class DispatchReturningProxyObject extends ProxyObject {

        private static IDispatch rawDispatch;

        DispatchReturningProxyObject() {
            super(IRawDispatchHandle.class, rawDispatch, new ObjectFactory());
        }

        @Override
        public IDispatch getRawDispatch() {
            return rawDispatch;
        }
    }

}
