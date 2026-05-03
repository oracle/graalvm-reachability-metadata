/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.icu.text;

import java.util.Comparator;
import java.util.Locale;

public final class Collator implements Comparator<String> {
    private final java.text.Collator delegate;

    public Collator(Locale locale) {
        this.delegate = java.text.Collator.getInstance(locale);
    }

    public static Collator getInstance(Locale locale) {
        return new Collator(locale);
    }

    public static Locale[] getAvailableLocales() {
        return new Locale[] { Locale.ENGLISH, Locale.US };
    }

    public void setStrength(int strength) {
        delegate.setStrength(strength);
    }

    @Override
    public int compare(String first, String second) {
        return delegate.compare(first, second);
    }
}
