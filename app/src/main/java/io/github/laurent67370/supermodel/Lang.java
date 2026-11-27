package io.github.laurent67370.supermodel;

import java.util.Locale;

/** The app's texts in French when the phone is in French, in English otherwise */
final class Lang {
    private Lang() {
    }

    static boolean french() {
        return "fr".equals(Locale.getDefault().getLanguage());
    }

    static String t(String english, String french) {
        return french() ? french : english;
    }
}
