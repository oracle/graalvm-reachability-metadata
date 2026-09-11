/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.dbi.DbConfigException;
import com.sleepycat.je.util.DbCacheSize;
import com.sleepycat.je.utilint.Adler32;
import com.sleepycat.je.utilint.BitMap;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.InternalException;
import com.sleepycat.je.utilint.PropUtil;
import com.sleepycat.je.utilint.Tracer;
import com.sleepycat.je.utilint.VLSN;
import com.sleepycat.util.ExceptionUnwrapper;
import com.sleepycat.util.FastInputStream;
import com.sleepycat.util.FastOutputStream;
import com.sleepycat.util.IOExceptionWrapper;
import com.sleepycat.util.PackedInteger;
import com.sleepycat.util.RuntimeExceptionWrapper;
import com.sleepycat.util.UtfOps;
import com.sleepycat.util.keyrange.KeyRange;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Set;
import java.util.zip.Checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CoreUtilityApiCoverageTest {

    @Test
    void checksumsBitMapsAndVlsnRoundTripUsefulValues() throws Exception {
        Adler32 checksum = new Adler32();
        checksum.update(1);
        checksum.update(new byte[] {2, 3, 4}, 1, 2);
        long value = checksum.getValue();
        assertThat(value).isPositive();
        checksum.reset();
        assertThat(checksum.getValue()).isEqualTo(1L);
        Checksum factoryChecksum = Adler32.makeChecksum();
        factoryChecksum.update(new byte[] {5}, 0, 1);
        assertThat(factoryChecksum.getValue()).isPositive();
        Adler32.ChunkingAdler32 chunked = new Adler32.ChunkingAdler32(2);
        chunked.update(new byte[] {7, 8, 9}, 0, 3);
        assertThat(chunked.getValue()).isPositive();

        BitMap bits = new BitMap();
        assertThat(bits.get(4)).isFalse();
        bits.set(4);
        assertThat(bits.get(4)).isTrue();
        assertThatThrownBy(() -> bits.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);

        VLSN original = new VLSN(3, 12, 99L);
        ByteBuffer buffer = ByteBuffer.allocate(original.getLogSize());
        original.writeToLog(buffer);
        buffer.flip();
        VLSN copy = new VLSN();
        copy.readFromLog(buffer, (byte) 0);
        assertThat(copy.getContentSize()).isEqualTo(original.getContentSize());
        assertThat(copy.getTransactionId()).isZero();
        ByteBuffer compact = ByteBuffer.allocate(original.getContentSize());
        original.writeToBuffer(compact);
        compact.flip();
        copy.readFromBuffer(compact);
        StringBuffer dump = new java.lang.StringBuffer();
        copy.dumpLog(dump, true);
        assertThat(dump).contains("sequence");
    }

    @Test
    void utilityConfigurationAndSerializationApisExposeContracts() throws Exception {
        DbCacheSize cache = new DbCacheSize(1000L, 2, 3, 4, 5, 6L);
        assertThat(cache.getNLevels()).isPositive();
        assertThat(cache.getMinCacheSizeInternalNodesOnly()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMaxCacheSizeInternalNodesOnly()).isGreaterThanOrEqualTo(
                cache.getMinCacheSizeInternalNodesOnly());
        assertThat(cache.getMinBtreeSizeInternalNodesOnly()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMaxBtreeSizeInternalNodesOnly()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMinCacheSizeWithData()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMaxCacheSizeWithData()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMinBtreeSizeWithData()).isGreaterThanOrEqualTo(0L);
        assertThat(cache.getMaxBtreeSizeWithData()).isGreaterThanOrEqualTo(0L);

        TransactionConfig transactionConfig = new TransactionConfig();
        transactionConfig.setNoWait(true);
        transactionConfig.setSync(true);
        assertThat(transactionConfig.getNoWait()).isTrue();
        assertThat(transactionConfig.getSync()).isTrue();
        StatsConfig statsConfig = new StatsConfig();
        ByteArrayOutputStream progress = new ByteArrayOutputStream();
        statsConfig.setShowProgressStream(new java.io.PrintStream(progress));
        assertThat(statsConfig.getShowProgressStream()).isNotNull();
        assertThatThrownBy(() -> new com.sleepycat.je.LogScanConfig().cloneConfig())
                .isInstanceOf(ClassCastException.class);

        ConfigParam parameter = new ConfigParam("je.test.parameter", "default", true, false,
                "test parameter");
        assertThat(parameter.getName()).isEqualTo("je.test.parameter");
        assertThat(parameter.getDefault()).isEqualTo("default");
        assertThat(parameter.getDescription()).isEqualTo("test parameter");
        assertThat(parameter.isMutable()).isTrue();
        parameter.setForReplication(true);
        assertThat(parameter.isForReplication()).isTrue();
        parameter.validateValue("value");
        parameter.validate();
        assertThat(parameter.toString()).contains("je.test.parameter");
        assertThatThrownBy(() -> new ConfigParam("", "x", false, false, "bad"))
                .isInstanceOf(IllegalArgumentException.class);

        Properties properties = new Properties();
        properties.setProperty("flag", "true");
        assertThat(PropUtil.getBoolean(properties, "flag")).isTrue();
        assertThat(PropUtil.microsToMillis(2500L)).isEqualTo(3L);
        assertThatThrownBy(() -> PropUtil.validateProp("unknown", Set.of("known"), "test"))
                .isInstanceOf(Exception.class);
        assertThat(new PropUtil()).isNotNull();
        assertThat(new DbConfigException("message").getMessage()).contains("message");
        assertThat(new DbConfigException(new Exception("cause")).getCause()).isNotNull();
        assertThat(new DbConfigException("message", new Exception("cause")).getCause())
                .isNotNull();
        assertThat(new InternalException()).isNotNull();
        assertThat(new InternalException("message").getMessage()).contains("message");
    }

    @Test
    void inputStreamsUtfAndPackedValuesFollowTheirContracts() throws Exception {
        FastInputStream input = new FastInputStream(new byte[] {1, 2, 3, 4});
        assertThat(input.markSupported()).isTrue();
        input.mark(4);
        byte[] first = new byte[2];
        assertThat(input.read(first)).isEqualTo(2);
        assertThat(first).containsExactly(1, 2);
        byte[] second = new byte[1];
        assertThat(input.readFast(second)).isEqualTo(1);
        assertThat(second).containsExactly(3);
        input.reset();
        assertThat(input.readFast()).isEqualTo(1);

        byte[] encoded = UtfOps.stringToBytes("cafe");
        assertThat(UtfOps.getCharLength(encoded)).isEqualTo(4);
        assertThat(new UtfOps()).isNotNull();
        assertThat(PackedInteger.getWriteIntLength(300)).isPositive();
        assertThat(new PackedInteger()).isNotNull();

        Throwable cause = new IllegalArgumentException("cause");
        IOExceptionWrapper ioWrapper = new IOExceptionWrapper(cause);
        assertThat(ioWrapper.getCause()).isSameAs(cause);
        assertThat(ioWrapper.getDetail()).isSameAs(cause);
        RuntimeExceptionWrapper runtimeWrapper = new RuntimeExceptionWrapper(cause);
        assertThat(runtimeWrapper.getCause()).isSameAs(cause);
        assertThat(runtimeWrapper.getDetail()).isSameAs(cause);

        KeyRange range = new KeyRange(null).subRange(new DatabaseEntry(new byte[] {2}));
        assertThat(range.getSingleKey().getData()).containsExactly(2);
        assertThat(range.toString()).isNotEmpty();
        DatabaseEntry copied = KeyRange.copy(new DatabaseEntry(new byte[] {7, 8}));
        assertThat(copied.getData()).containsExactly(7, 8);
        assertThat(KeyRange.toString(new DatabaseEntry(new byte[] {1}))).contains("1");
    }

    @Test
    void diagnosticValuesAndOutputStreamsHaveObservableBehavior() throws Exception {
        Tracer tracer = new Tracer("coverage message");
        Tracer same = new Tracer("coverage message");
        assertThat(tracer.getMessage()).isEqualTo("coverage message");
        assertThat(tracer.getTransactionId()).isZero();
        assertThat(tracer).isEqualTo(tracer);
        assertThat(tracer).isNotEqualTo(new Tracer("different message"));
        assertThat(tracer.hashCode()).isNotZero();
        StringBuffer dump = new java.lang.StringBuffer();
        tracer.dumpLog(dump, true);
        assertThat(dump).contains("coverage message");
        assertThat(tracer.toString()).contains("coverage message");
        ByteBuffer tracerBuffer = ByteBuffer.allocate(tracer.getLogSize());
        tracer.writeToLog(tracerBuffer);
        tracerBuffer.flip();
        new Tracer().readFromLog(tracerBuffer, (byte) 0);
        assertThat(Tracer.getStackTrace(new IllegalStateException("failure")))
                .contains("failure");

        FastOutputStream sized = new FastOutputStream(4);
        assertThat(sized).isNotNull();
        FastOutputStream backed = new FastOutputStream(new byte[] {8, 9}, 1);
        assertThat(backed.toString("UTF-8")).isEmpty();

        FastOutputStream stream = new FastOutputStream(1, 1);
        stream.write(10);
        stream.write(new byte[] {20, 30}, 0, 2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.writeTo(output);
        assertThat(output.toByteArray()).containsExactly(10, 20, 30);
        assertThat(stream.getBufferLength()).isGreaterThanOrEqualTo(stream.size());
        stream.addSize(1);
        assertThat(stream.size()).isEqualTo(4);
        stream.reset();
        assertThat(stream.toByteArray()).isEmpty();
        assertThat(new FastOutputStream(new byte[] {1, 2}).toString()).isNotNull();

        Exception wrapped = new Exception(new IllegalArgumentException("inner"));
        assertThat(ExceptionUnwrapper.unwrap(wrapped)).isSameAs(wrapped);
        assertThat(ExceptionUnwrapper.unwrapAny(wrapped)).isSameAs(wrapped);
        assertThat(new ExceptionUnwrapper()).isNotNull();
        assertThat(CmdUtil.getArg(new String[] {"a", "b"}, 1)).isEqualTo("b");
        assertThat(CmdUtil.readLongNumber("0x10")).isEqualTo(16L);
        assertThat(CmdUtil.readLongNumber("25")).isEqualTo(25L);
        assertThat(new CmdUtil()).isNotNull();
        assertThat(new DatabaseEntry(new byte[] {1})).isNotNull();
    }
}
