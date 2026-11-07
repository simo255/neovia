package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Rating votes are counts of ratings, not necessarily written reviews. */
final class RatingData {
    private static final String[] SOURCES = {"imdb", "tmdb", "metacritic", "letterboxd"};
    private static final String[] NAMES = {"IMDb", "TMDB", "Metacritic", "Letterboxd"};
    private final Map<String, Entry> entries = new HashMap<>();
    private long tmdbReviews = -1;
    private static final class Entry {
        final double value;
        final long votes;
        Entry(double value, long votes) { this.value = value; this.votes = votes; }
    }
    static boolean positiveScore(String value) {
        try {
            double number = Double.parseDouble(value.trim());
            return Double.isFinite(number) && number > 0;
        } catch (Exception ignored) { return false; }
    }
    static RatingData parse(JSONObject response) {
        RatingData result = new RatingData();
        JSONArray ratings = response.optJSONArray("ratings");
        if (ratings == null) return result;
        for (int i = 0; i < Math.min(ratings.length(), 50); i++) {
            JSONObject row = ratings.optJSONObject(i);
            if (row == null) continue;
            String source = row.optString("source", "");
            int index = -1;
            for (int j = 0; j < SOURCES.length; j++) if (SOURCES[j].equals(source)) index = j;
            if (index < 0 || row.isNull("value")) continue;
            double value = row.optDouble("value", Double.NaN);
            double max = "metacritic".equals(source) ? 100 : "letterboxd".equals(source) ? 5 : 10;
            if (!Double.isFinite(value) || value <= 0 || value > max) continue;
            long votes = row.isNull("votes") ? -1 : row.optLong("votes", -1);
            result.entries.putIfAbsent(source, new Entry(value, votes >= 0 ? votes : -1));
        }
        return result;
    }
    void tmdb(JSONObject item, long reviews) {
        double value = item.optDouble("vote_average", Double.NaN);
        long count = item.isNull("vote_count") ? -1 : item.optLong("vote_count", -1);
        if (Double.isFinite(value) && value > 0 && value <= 10) entries.put("tmdb", new Entry(value, count >= 0 ? count : -1));
        if (reviews >= 0) tmdbReviews = reviews;
    }
    static final class Cell {
        final String name, score, counts;
        Cell(String name, String score, String counts) { this.name = name; this.score = score; this.counts = counts; }
    }
    List<Cell> cells(Locale locale, String votesLabel, String reviewsLabel) {
        List<Cell> parts = new ArrayList<>();
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMaximumFractionDigits(1);
        NumberFormat count = NumberFormat.getIntegerInstance(locale);
        for (int i = 0; i < SOURCES.length; i++) {
            Entry e = entries.get(SOURCES[i]);
            if (e == null) continue;
            String suffix = "metacritic".equals(SOURCES[i]) ? "/100" : "letterboxd".equals(SOURCES[i]) ? "/5" : "/10";
            String counts = e.votes > 0 ? count.format(e.votes) + " " + votesLabel : "";
            if (i == 1 && tmdbReviews > 0) counts += (counts.isEmpty() ? "" : " · ") + count.format(tmdbReviews) + " " + reviewsLabel;
            parts.add(new Cell(NAMES[i], number.format(e.value) + suffix, counts));
        }
        if (!entries.containsKey("tmdb") && tmdbReviews > 0)
            parts.add(new Cell("TMDB", "", count.format(tmdbReviews) + " " + reviewsLabel));
        return parts;
    }
    String format(Locale locale, String votesLabel, String reviewsLabel) {
        List<String> parts = new ArrayList<>();
        for (Cell cell : cells(locale, votesLabel, reviewsLabel)) {
            String label = cell.name + (cell.score.isEmpty() ? "" : " " + cell.score);
            if (!cell.counts.isEmpty()) label += cell.score.isEmpty() ? " " + cell.counts : " (" + cell.counts + ")";
            parts.add(label);
        }
        return String.join("   ·   ", parts);
    }
}
