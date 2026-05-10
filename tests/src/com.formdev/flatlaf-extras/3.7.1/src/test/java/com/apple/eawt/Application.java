/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.apple.eawt;

public class Application {
    private static final Application INSTANCE = new Application();

    private AboutHandler aboutHandler;
    private QuitHandler quitHandler;

    public static Application getApplication() {
        return INSTANCE;
    }

    public static void resetHandlers() {
        INSTANCE.aboutHandler = null;
        INSTANCE.quitHandler = null;
    }

    public void setAboutHandler(AboutHandler aboutHandler) {
        this.aboutHandler = aboutHandler;
    }

    public void setQuitHandler(QuitHandler quitHandler) {
        this.quitHandler = quitHandler;
    }

    public boolean dispatchQuitRequest(QuitResponse response) {
        if (quitHandler == null)
            return false;

        quitHandler.handleQuitRequestWith(new AppEvent.QuitEvent(), response);
        return true;
    }

    public boolean hasAboutHandler() {
        return aboutHandler != null;
    }

    public boolean hasQuitHandler() {
        return quitHandler != null;
    }
}
