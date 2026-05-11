/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package java.nio;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.FileDescriptor;
import java.lang.foreign.MemorySegment;

/**
 * Test-only compatibility shim for the legacy constructor used by slice 0.36.
 */
final class DirectByteBuffer
        extends MappedByteBuffer
        implements DirectBuffer {
    private final Object attachment;

    DirectByteBuffer(int capacity) {
        this(UNSAFE.allocateMemory(capacity), capacity, null, null);
    }

    DirectByteBuffer(long address, int capacity, Object attachment) {
        this(address, capacity, attachment, null);
    }

    DirectByteBuffer(long address, int capacity, Object attachment, MemorySegment segment) {
        super(-1, 0, capacity, capacity, null, false, segment);
        this.address = address;
        this.attachment = attachment;
    }

    DirectByteBuffer(long address, int capacity, Object attachment, FileDescriptor descriptor, boolean sync,
            MemorySegment segment) {
        super(-1, 0, capacity, capacity, descriptor, sync, segment);
        this.address = address;
        this.attachment = attachment;
    }

    private DirectByteBuffer(long address, long capacity) {
        this(address, Math.toIntExact(capacity), null, null);
    }

    protected DirectByteBuffer(int capacity, long address, FileDescriptor descriptor, Runnable unmapper, boolean sync,
            MemorySegment segment) {
        super(-1, 0, capacity, capacity, descriptor, sync, segment);
        this.address = address;
        this.attachment = unmapper;
    }

    DirectByteBuffer(DirectBuffer buffer, int mark, int position, int limit, int capacity, int offset,
            FileDescriptor descriptor, boolean sync, MemorySegment segment) {
        super(mark, position, limit, capacity, descriptor, sync, segment);
        this.address = buffer.address() + offset;
        this.attachment = buffer;
    }

    @Override
    public Object attachment() {
        return attachment;
    }

    @Override
    public Cleaner cleaner() {
        return null;
    }

    @Override
    public long address() {
        return address;
    }

    @Override
    Object base() {
        return null;
    }

    @Override
    int scaleShifts() {
        return 0;
    }

    @Override
    AbstractMemorySegmentImpl heapSegment(Object base, long offset, long length, boolean readOnly,
            MemorySessionImpl session) {
        return null;
    }

    @Override
    public MappedByteBuffer slice() {
        return slice(0, remaining());
    }

    @Override
    public MappedByteBuffer slice(int index, int length) {
        return new DirectByteBuffer(address + index, length, attachment);
    }

    @Override
    public MappedByteBuffer duplicate() {
        DirectByteBuffer duplicate = new DirectByteBuffer(address, capacity(), attachment);
        duplicate.position(position());
        duplicate.limit(limit());
        return duplicate;
    }

    @Override
    public ByteBuffer asReadOnlyBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte get() {
        int index = position();
        position(index + 1);
        return get(index);
    }

    @Override
    public ByteBuffer put(byte value) {
        int index = position();
        position(index + 1);
        return put(index, value);
    }

    @Override
    public byte get(int index) {
        return UNSAFE.getByte(address + index);
    }

    @Override
    public ByteBuffer put(int index, byte value) {
        UNSAFE.putByte(address + index, value);
        return this;
    }

    @Override
    public MappedByteBuffer compact() {
        return this;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public char getChar() {
        return (char) getShort();
    }

    @Override
    public ByteBuffer putChar(char value) {
        return putShort((short) value);
    }

    @Override
    public char getChar(int index) {
        return (char) getShort(index);
    }

    @Override
    public ByteBuffer putChar(int index, char value) {
        return putShort(index, (short) value);
    }

    @Override
    public CharBuffer asCharBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort() {
        int index = position();
        position(index + Short.BYTES);
        return getShort(index);
    }

    @Override
    public ByteBuffer putShort(short value) {
        int index = position();
        position(index + Short.BYTES);
        return putShort(index, value);
    }

    @Override
    public short getShort(int index) {
        return UNSAFE.getShort(null, address + index);
    }

    @Override
    public ByteBuffer putShort(int index, short value) {
        UNSAFE.putShort(null, address + index, value);
        return this;
    }

    @Override
    public ShortBuffer asShortBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt() {
        int index = position();
        position(index + Integer.BYTES);
        return getInt(index);
    }

    @Override
    public ByteBuffer putInt(int value) {
        int index = position();
        position(index + Integer.BYTES);
        return putInt(index, value);
    }

    @Override
    public int getInt(int index) {
        return UNSAFE.getInt(null, address + index);
    }

    @Override
    public ByteBuffer putInt(int index, int value) {
        UNSAFE.putInt(null, address + index, value);
        return this;
    }

    @Override
    public IntBuffer asIntBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong() {
        int index = position();
        position(index + Long.BYTES);
        return getLong(index);
    }

    @Override
    public ByteBuffer putLong(long value) {
        int index = position();
        position(index + Long.BYTES);
        return putLong(index, value);
    }

    @Override
    public long getLong(int index) {
        return UNSAFE.getLong(null, address + index);
    }

    @Override
    public ByteBuffer putLong(int index, long value) {
        UNSAFE.putLong(null, address + index, value);
        return this;
    }

    @Override
    public LongBuffer asLongBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    @Override
    public ByteBuffer putFloat(float value) {
        return putInt(Float.floatToRawIntBits(value));
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public ByteBuffer putFloat(int index, float value) {
        return putInt(index, Float.floatToRawIntBits(value));
    }

    @Override
    public FloatBuffer asFloatBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    @Override
    public ByteBuffer putDouble(double value) {
        return putLong(Double.doubleToRawLongBits(value));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public ByteBuffer putDouble(int index, double value) {
        return putLong(index, Double.doubleToRawLongBits(value));
    }

    @Override
    public DoubleBuffer asDoubleBuffer() {
        throw new UnsupportedOperationException();
    }
}
