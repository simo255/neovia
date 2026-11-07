package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class RatingDataTest {
    @Test public void scoresAndVotesKeepTheirOriginalScalesAndSources() throws Exception {
        JSONArray rows = new JSONArray()
                .put(new JSONObject().put("source", "imdb").put("value", 8.7).put("votes", 1500))
                .put(new JSONObject().put("source", "tomatoes").put("value", 83).put("votes", JSONObject.NULL))
                .put(new JSONObject().put("source", "metacritic").put("value", 73).put("votes", 10))
                .put(new JSONObject().put("source", "other").put("value", 9).put("votes", 1));
        RatingData data = RatingData.parse(new JSONObject().put("ratings", rows));
        String label = data.format(Locale.US, "votes", "written reviews in English");
        assertTrue(label.contains("IMDb 8.7/10 (1,500 votes)"));
        assertFalse(label.contains("RT"));
        assertTrue(label.contains("Metacritic 73/100 (10 votes)"));
        assertFalse(label.contains("other"));
        assertFalse(label.contains("written reviews"));
    }
    @Test public void rottenTomatoesScoresAreNotShown() throws Exception {
        JSONArray rows = new JSONArray()
                .put(new JSONObject().put("source", "tomatoes").put("value", 92).put("votes", 100))
                .put(new JSONObject().put("source", "popcorn").put("value", 85).put("votes", 300))
                .put(new JSONObject().put("source", "letterboxd").put("value", 4.2).put("votes", 70));
        RatingData data = RatingData.parse(new JSONObject().put("ratings", rows));
        assertEquals("Letterboxd 4.2/5 (70 votes)", data.format(Locale.US, "votes", "reviews EN"));
    }
    @Test public void tmdbVotesAndWrittenReviewsStaySeparate() throws Exception {
        RatingData data = RatingData.parse(new JSONObject().put("ratings", new JSONArray()));
        data.tmdb(new JSONObject().put("vote_average", 8.2).put("vote_count", 250), 6);
        String label = data.format(Locale.US, "votes", "written reviews in English");
        assertTrue(label.contains("TMDB 8.2/10 (250 votes · 6 written reviews in English)"));
    }
    @Test public void missingVotesAndReviewsAreNotInvented() throws Exception {
        RatingData data = RatingData.parse(new JSONObject().put("ratings", new JSONArray().put(
                new JSONObject().put("source", "tmdb").put("value", 7.4))));
        String label = data.format(Locale.US, "votes", "written reviews in English");
        assertEquals("TMDB 7.4/10", label);
        data.tmdb(new JSONObject(), 3);
        assertTrue(data.format(Locale.US, "votes", "written reviews in English").contains("3 written reviews in English"));
    }
    @Test public void writtenReviewsAreStillShownWhenTheTmdbScoreIsUnavailable() {
        RatingData data = new RatingData();
        data.tmdb(new JSONObject(), 4);
        assertEquals("TMDB 4 written reviews in English", data.format(Locale.US, "votes", "written reviews in English"));
    }
    @Test public void onlySafeKeyCharactersAreAccepted() {
        assertTrue(RatingKey.valid("0123456789abcdef0123456789abcdef", false));
        assertTrue(RatingKey.valid("eyJhbGciOiJIUzI1NiJ9.test.payload", true));
        assertFalse(RatingKey.valid("secret&other=badvalue", false));
        assertFalse(RatingKey.valid("short", true));
    }
    @Test public void zeroScoresVotesAndReviewCountsAreNotDisplayed() throws Exception {
        JSONArray rows = new JSONArray()
                .put(new JSONObject().put("source", "imdb").put("value", 7.2).put("votes", 0))
                .put(new JSONObject().put("source", "tomatoes").put("value", 0).put("votes", 5));
        RatingData data = RatingData.parse(new JSONObject().put("ratings", rows));
        data.tmdb(new JSONObject().put("vote_average", 7.4).put("vote_count", 0), 0);
        String result = data.format(Locale.US, "votes", "reviews EN");
        assertEquals("IMDb 7.2/10   ·   TMDB 7.4/10", result);
        assertFalse(result.contains("0 votes"));
        assertFalse(result.contains("0 reviews"));
        assertFalse(result.contains("RT"));
        RatingData reviewOnly = new RatingData(); reviewOnly.tmdb(new JSONObject(), 0);
        assertTrue(reviewOnly.cells(Locale.US, "votes", "reviews EN").isEmpty());
        assertFalse(RatingData.positiveScore("0"));
        assertFalse(RatingData.positiveScore("0.0"));
        assertFalse(RatingData.positiveScore("N/A"));
        assertTrue(RatingData.positiveScore("7.2"));
    }
    @Test public void rejectsInvalidScoresAndIgnoresDuplicates() throws Exception {
        JSONArray rows = new JSONArray().put(new JSONObject().put("source", "imdb").put("value", 12))
                .put(new JSONObject().put("source", "imdb").put("value", 6.5))
                .put(new JSONObject().put("source", "imdb").put("value", 9.5));
        assertEquals("IMDb 6.5/10", RatingData.parse(new JSONObject().put("ratings", rows)).format(Locale.US, "votes", "reviews"));
    }
}
