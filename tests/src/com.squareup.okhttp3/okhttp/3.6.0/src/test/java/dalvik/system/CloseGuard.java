/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dalvik.system;

public final class CloseGuard {
    private String closer;
    private boolean warned;

    private CloseGuard() {
    }

    public static CloseGuard get() {
        return new CloseGuard();
    }

    public void open(String closer) {
        this.closer = closer;
    }

    public void warnIfOpen() {
        this.warned = closer != null;
    }

    public String closer() {
        return closer;
    }

    public boolean warned() {
        return warned;
    }
}
