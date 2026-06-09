/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package reflectasm.java.util;

/**
 * Occupies the constructor accessor class name that ReflectASM derives from {@link java.util.ArrayList},
 * so Kryo falls back to reflective construction in Native Image.
 */
public class ArrayListConstructorAccess {
    public ArrayListConstructorAccess() {
    }
}
