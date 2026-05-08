/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.misc;

/** Compatibility shim for libraries that still probe the pre-Java 9 Cleaner type. */
public final class Cleaner {
    public void clean() {
    }
}
