package com.ayane.dynamicisland;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/** Local-only typography helper. No downloaded fonts or external libraries. */
public final class AyaneTypography {
    private AyaneTypography() {}

    public static Typeface resolve(Context context, boolean bold) {
        SharedPreferences prefs = context.getSharedPreferences("island_settings", Context.MODE_PRIVATE);
        String style = prefs.getString("font_family", "rounded");
        String family;
        if ("modern".equals(style)) family = "sans-serif";
        else if ("compact".equals(style)) family = "sans-serif-condensed";
        else family = "sans-serif-rounded";
        return Typeface.create(family, bold ? Typeface.BOLD : Typeface.NORMAL);
    }
}
