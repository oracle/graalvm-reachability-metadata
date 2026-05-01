/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory;
import net.sourceforge.htmlunit.corejs.javascript.ContextListener;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextTest {
    private static final String DEBUGGER_CLASS_NAME =
            "net.sourceforge.htmlunit.corejs.javascript.tools.debugger.Main";

    private static final byte[] DEBUGGER_LISTENER_BYTES =
            Base64.getDecoder()
                    .decode(
                            ""
                                    + "yv66vgAAAEUAHgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0"
                                    + "AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAPm5ldC9zb3VyY2Vm"
                                    + "b3JnZS9odG1sdW5pdC9jb3JlanMvamF2YXNjcmlwdC90b29scy9kZWJ1"
                                    + "Z2dlci9NYWluAQAPYXR0YWNoZWRGYWN0b3J5AQA7TG5ldC9zb3VyY2Vm"
                                    + "b3JnZS9odG1sdW5pdC9jb3JlanMvamF2YXNjcmlwdC9Db250ZXh0RmFj"
                                    + "dG9yeTsHAA4BADpuZXQvc291cmNlZm9yZ2UvaHRtbHVuaXQvY29yZWpz"
                                    + "L2phdmFzY3JpcHQvQ29udGV4dExpc3RlbmVyBwAQAQBFbmV0X3NvdXJj"
                                    + "ZWZvcmdlX2h0bWx1bml0L2h0bWx1bml0X2NvcmVfanMvQ29udGV4dFRl"
                                    + "c3QkQXR0YWNobWVudFByb2JlAQAEQ29kZQEACGF0dGFjaFRvAQA+KExu"
                                    + "ZXQvc291cmNlZm9yZ2UvaHRtbHVuaXQvY29yZWpzL2phdmFzY3JpcHQv"
                                    + "Q29udGV4dEZhY3Rvcnk7KVYBAD0oKUxuZXQvc291cmNlZm9yZ2UvaHRt"
                                    + "bHVuaXQvY29yZWpzL2phdmFzY3JpcHQvQ29udGV4dEZhY3Rvcnk7AQAO"
                                    + "Y29udGV4dEVudGVyZWQBADcoTG5ldC9zb3VyY2Vmb3JnZS9odG1sdW5p"
                                    + "dC9jb3JlanMvamF2YXNjcmlwdC9Db250ZXh0OylWAQANY29udGV4dEV4"
                                    + "aXRlZAEADmNvbnRleHRDcmVhdGVkAQAPY29udGV4dFJlbGVhc2VkAQAM"
                                    + "SW5uZXJDbGFzc2VzBwAcAQA1bmV0X3NvdXJjZWZvcmdlX2h0bWx1bml0"
                                    + "L2h0bWx1bml0X2NvcmVfanMvQ29udGV4dFRlc3QBAA9BdHRhY2htZW50"
                                    + "UHJvYmUAMQAIAAIAAgANAA8AAQACAAsADAAAAAcAAQAFAAYAAQARAAAA"
                                    + "EQABAAEAAAAFKrcAAbEAAAAAAAEAEgATAAEAEQAAABIAAgACAAAABior"
                                    + "tQAHsQAAAAAAAQALABQAAQARAAAAEQABAAEAAAAFKrQAB7AAAAAAAAEA"
                                    + "FQAWAAEAEQAAAA0AAAACAAAAAbEAAAAAAAEAFwAWAAEAEQAAAA0AAAAC"
                                    + "AAAAAbEAAAAAAAEAGAAWAAEAEQAAAA0AAAACAAAAAbEAAAAAAAEAGQAW"
                                    + "AAEAEQAAAA0AAAACAAAAAbEAAAAAAAEAGgAAAAoAAQAPABsAHQYJ");

    @Test
    @SuppressWarnings("deprecation")
    void addContextListenerAttachesDebuggerListenerThroughCompatibilityHook() throws Throwable {
        try {
            AttachmentProbe listener = newDebuggerListener();

            Context.addContextListener((ContextListener) listener);

            assertThat(listener.attachedFactory()).isSameAs(ContextFactory.getGlobal());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static AttachmentProbe newDebuggerListener() throws Throwable {
        Class<?> listenerClass = new DebuggerListenerClassLoader().defineDebuggerListener();
        MethodHandle constructor =
                MethodHandles.publicLookup()
                        .findConstructor(listenerClass, MethodType.methodType(void.class));
        Object listener = constructor.invoke();

        return AttachmentProbe.class.cast(listener);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface AttachmentProbe {
        ContextFactory attachedFactory();
    }

    private static final class DebuggerListenerClassLoader extends ClassLoader {
        private DebuggerListenerClassLoader() {
            super(ContextTest.class.getClassLoader());
        }

        private Class<?> defineDebuggerListener() {
            byte[] bytes = DEBUGGER_LISTENER_BYTES;
            return defineClass(DEBUGGER_CLASS_NAME, bytes, 0, bytes.length);
        }
    }
}
