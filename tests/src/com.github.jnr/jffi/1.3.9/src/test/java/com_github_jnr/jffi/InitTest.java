/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Function;
import com.kenai.jffi.HeapInvocationBuffer;
import com.kenai.jffi.Invoker;
import com.kenai.jffi.Library;
import com.kenai.jffi.Type;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InitTest {
    @Test
    void initializesNativeSupportAndInvokesAnIntegerFunction() {
        Library library = NativeLibrarySupport.standardLibrary();
        Function absolute = new Function(NativeLibrarySupport.symbol(library, "abs"), Type.SINT, Type.SINT);
        try {
            HeapInvocationBuffer negative = new HeapInvocationBuffer(absolute);
            negative.putInt(-73);
            assertEquals(73, Invoker.getInstance().invokeInt(absolute, negative));

            HeapInvocationBuffer positive = new HeapInvocationBuffer(absolute);
            positive.putInt(19);
            assertEquals(19, Invoker.getInstance().invokeInt(absolute, positive));
        } finally {
            Reference.reachabilityFence(absolute);
            Reference.reachabilityFence(library);
        }
    }
}
