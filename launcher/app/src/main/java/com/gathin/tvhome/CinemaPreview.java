package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import java.util.List;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Title details and compact ratings; artwork stays in the poster strip below. */
final class CinemaPreview extends LinearLayout {
    private final TextView title, meta, summary;
    private final LinearLayout ratingsRow;
    private String ratingsSignature = "";
    private boolean animate;
    private String selected = "";

    void motion(boolean enabled, boolean reduced) { animate = enabled && !reduced; }

    CinemaPreview(Context context) {
        super(context);
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(3), dp(1), dp(12), dp(1));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        title = label(31, HomeAppearance.TEXT); title.setMaxLines(1);
        title.setFontVariationSettings("'wght' 550"); title.setLetterSpacing(-0.02f);
        title.setAutoSizeTextTypeUniformWithConfiguration(24, 31, 1, TypedValue.COMPLEX_UNIT_SP);
        LayoutParams heading = new LayoutParams(-1, dp(34)); heading.topMargin = dp(2); heading.bottomMargin = dp(2);
        addView(title, heading);
        meta = label(13, HomeAppearance.MUTED); meta.setMaxLines(1);
        addView(meta, new LayoutParams(-1, -2));
        summary = label(14, HomeAppearance.MUTED); summary.setMaxLines(2);
        summary.setLineSpacing(dp(2), 1f);
        LayoutParams body = new LayoutParams(-1, -2); body.topMargin = dp(4);
        addView(summary, body);
        ratingsRow = new LinearLayout(context); ratingsRow.setOrientation(HORIZONTAL);
        ratingsRow.setVisibility(GONE);
        LayoutParams ratingsParams = new LayoutParams(-1, -2); ratingsParams.topMargin = dp(3);
        addView(ratingsRow, ratingsParams);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        view.setTypeface(getResources().getFont(R.font.outfit), Typeface.NORMAL);
        view.setFontVariationSettings("'wght' 450");
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    void show(CatalogItem item, String detail, List<RatingData.Cell> ratings) {
        String key = item.type + ":" + item.id;
        boolean changed = !selected.equals(key);
        selected = key;
        if (!item.name.contentEquals(title.getText())) title.setText(item.name);
        String facts = detail;
        if (ratings.isEmpty() && RatingData.positiveScore(item.rating)) facts += (facts.isEmpty() ? "" : "   ·   ") + "★ " + item.rating;
        String genres = item.genreLabel();
        if (!genres.isEmpty()) facts += (facts.isEmpty() ? "" : "   ·   ") + genres;
        meta.setMaxLines(1);
        showRatings(ratings);
        if (!facts.contentEquals(meta.getText())) meta.setText(facts);
        meta.setVisibility(facts.isEmpty() ? INVISIBLE : VISIBLE);
        if (!item.description.contentEquals(summary.getText())) summary.setText(item.description);
        summary.setVisibility(item.description.isEmpty() ? GONE : VISIBLE);
        setVisibility(VISIBLE);
        if (changed) Motion.enter(this, dp(4), animate);
    }

    private void showRatings(List<RatingData.Cell> ratings) {
        StringBuilder signature = new StringBuilder();
        for (RatingData.Cell cell : ratings) signature.append(cell.name).append('\n').append(cell.score).append('\n').append(cell.counts).append('\n');
        if (!signature.toString().equals(ratingsSignature)) {
            ratingsSignature = signature.toString();
            ratingsRow.removeAllViews();
            for (RatingData.Cell cell : ratings) {
                LinearLayout column = new LinearLayout(getContext()); column.setOrientation(VERTICAL);
                column.setPadding(0, 0, dp(10), 0);
                LinearLayout primary = new LinearLayout(getContext());
                primary.setOrientation(HORIZONTAL); primary.setGravity(Gravity.CENTER_VERTICAL);
                ImageView logo = new ImageView(getContext());
                int icon, width;
                switch (cell.name) {
                    case "IMDb": icon = R.drawable.rating_imdb; width = 36; break;
                    case "TMDB": icon = R.drawable.rating_tmdb; width = 64; break;
                    case "Metacritic": icon = R.drawable.rating_metacritic; width = 20; break;
                    case "Letterboxd": icon = R.drawable.rating_letterboxd; width = 40; break;
                    default: icon = 0; width = 0;
                }
                if (icon != 0) {
                    logo.setImageResource(icon); logo.setContentDescription(cell.name);
                    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if (icon == R.drawable.rating_imdb) logo.setBackground(HomeAppearance.surface(dp(3), 0xFFF5C518, 0, 0));
                    primary.addView(logo, new LayoutParams(dp(width), dp(20)));
                }
                TextView score = label(16, HomeAppearance.TEXT); score.setMaxLines(1);
                score.setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1, TypedValue.COMPLEX_UNIT_SP);
                score.setText(icon == 0 ? cell.name + "  " + cell.score : cell.score);
                score.setPadding(icon == 0 ? 0 : dp(6), 0, 0, 0);
                score.setGravity(Gravity.CENTER_VERTICAL);
                score.setShadowLayer(dp(2), 0, dp(1), 0xE6000000);
                primary.addView(score, new LayoutParams(0, dp(20), 1));
                column.addView(primary, new LayoutParams(-1, dp(20)));
                TextView count = label(13, HomeAppearance.MUTED); count.setMaxLines(1);
                count.setAutoSizeTextTypeUniformWithConfiguration(11, 13, 1, TypedValue.COMPLEX_UNIT_SP);
                count.setText(cell.counts); count.setVisibility(cell.counts.isEmpty() ? INVISIBLE : VISIBLE);
                count.setShadowLayer(dp(2), 0, dp(1), 0xE6000000);
                column.addView(count, new LayoutParams(-1, dp(16)));
                ratingsRow.addView(column, new LayoutParams(0, -2, 1));
            }
        }
        ratingsRow.setVisibility(ratings.isEmpty() ? GONE : VISIBLE);
    }

    void clear() { Motion.reset(this); selected = ""; setVisibility(INVISIBLE); }

    /** A small text shadow complements the apps-first page's soft wallpaper gradient. */
    void wallpaperContrast() {
        for (TextView text : new TextView[]{title, meta, summary})
            text.setShadowLayer(dp(2), 0, dp(1), 0xE6000000);
        for (int i = 0; i < ratingsRow.getChildCount(); i++) {
            LinearLayout column = (LinearLayout) ratingsRow.getChildAt(i);
            for (int j = 0; j < column.getChildCount(); j++)
                ((TextView) column.getChildAt(j)).setShadowLayer(dp(2), 0, dp(1), 0xE6000000);
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int lines = ratingsRow.getVisibility() == VISIBLE ? 2 : height >= dp(128) ? 3 : 2;
        if (summary.getMinLines() != 0) summary.setMinLines(0);
        if (summary.getMaxLines() != lines) summary.setMaxLines(lines);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
