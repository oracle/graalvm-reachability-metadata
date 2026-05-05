/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.h2.engine.SysProperties;
import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sun.misc.Unsafe;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    @Test
    void unmapsMappedBufferWithCleanerHack(@TempDir Path temporaryDirectory) throws IOException {
        Path mappedFile = temporaryDirectory.resolve("mapped.dat");
        Files.write(mappedFile, new byte[4096]);

        MappedByteBuffer mappedBuffer;
        try (FileChannel channel = FileChannel.open(mappedFile, READ, WRITE)) {
            mappedBuffer = channel.map(READ_WRITE, 0, 4096);
        }
        mappedBuffer.put(0, (byte) 42);

        assertThat(SysProperties.NIO_CLEANER_HACK).isTrue();
        assertThat(MemoryUnmapper.unmap(mappedBuffer)).isTrue();
    }

    @Test
    void unmapsDirectBufferThroughLegacyCleanerPath() throws Exception {
        assertThat(MemoryUnmapper.unmap(ByteBuffer.allocateDirect(1))).isTrue();

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(32);
        directBuffer.put(0, (byte) 23);

        StaticFieldAccess invokeCleanerField = staticField("INVOKE_CLEANER");
        Method originalInvokeCleaner = (Method) invokeCleanerField.get();
        invokeCleanerField.set(null);
        try {
            assertThat(invokeCleanerField.get()).isNull();
            MemoryUnmapper.unmap(directBuffer);
        } finally {
            invokeCleanerField.set(originalInvokeCleaner);
        }
    }

    private static StaticFieldAccess staticField(String name) throws Exception {
        Field field = MemoryUnmapper.class.getDeclaredField(name);
        Unsafe unsafe = unsafe();
        return new StaticFieldAccess(unsafe, unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class StaticFieldAccess {
        private final Unsafe unsafe;
        private final Object base;
        private final long offset;

        private StaticFieldAccess(Unsafe unsafe, Object base, long offset) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
        }

        private Object get() {
            return unsafe.getObjectVolatile(base, offset);
        }

        private void set(Object value) {
            unsafe.putObjectVolatile(base, offset, value);
        }
    }
}
