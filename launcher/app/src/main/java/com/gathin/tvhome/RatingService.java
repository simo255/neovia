package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional on-focus lookup; keys never leave the device except for their respective HTTPS APIs. */
final class RatingService {
    private static final long TTL_MS = 24L * 60 * 60 * 1000;
    private final RatingKey mdb, tmdb;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>(80, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) { return size() > 80; }
    };
    private final java.util.Set<String> pending = new java.util.HashSet<>();
    private long retryAfter;
    private volatile long mdbRetryAfter, tmdbRetryAfter;
    private volatile int generation;
    private volatile boolean closed;
    private static final class Cached {
        final RatingData data; final long at, ttl;
        Cached(RatingData data, long at, long ttl) { this.data = data; this.at = at; this.ttl = ttl; }
    }
    interface Listener { void ready(RatingData data); }
    RatingService(Context context) { mdb = new RatingKey(context, false); tmdb = new RatingKey(context, true); }
    boolean configured() { return mdb.configured() || tmdb.configured(); }
    boolean configure(String mdbValue, String tmdbValue) {
        if (mdbValue.isEmpty() && tmdbValue.isEmpty()) return false;
        if ((!mdbValue.isEmpty() && !RatingKey.valid(mdbValue, false)) || (!tmdbValue.isEmpty() && !RatingKey.valid(tmdbValue, true))) return false;
        try {
            if (!mdbValue.isEmpty()) mdb.save(mdbValue);
            if (!tmdbValue.isEmpty()) tmdb.save(tmdbValue);
            generation++; cache.clear(); pending.clear(); retryAfter = 0; mdbRetryAfter = 0; tmdbRetryAfter = 0; return true;
        } catch (Exception ignored) { generation++; cache.clear(); pending.clear(); return false; }
    }
    void clear() { mdb.clear(); tmdb.clear(); generation++; cache.clear(); pending.clear(); retryAfter = 0; mdbRetryAfter = 0; tmdbRetryAfter = 0; }
    RatingData cached(String type, String id) {
        Cached value = cache.get(type + ":" + id);
        return value != null && SystemClock.elapsedRealtime() - value.at < value.ttl ? value.data : null;
    }
    void request(String type, String id, Listener listener) {
        if (closed || !configured() || !id.matches("tt[0-9]+") || !("movie".equals(type) || "series".equals(type))) return;
        RatingData known = cached(type, id);
        if (known != null) { listener.ready(known); return; }
        if (SystemClock.elapsedRealtime() < retryAfter) return;
        int requestedGeneration = generation;
        String identity = type + ":" + id;
        if (!pending.add(identity)) return;
        worker.execute(() -> {
            RatingData data = new RatingData();
            boolean success = false, failed = false;
            long cooldown = 60_000L;
            if ((mdb.configured() && SystemClock.elapsedRealtime() < mdbRetryAfter)
                    || (tmdb.configured() && SystemClock.elapsedRealtime() < tmdbRetryAfter)) failed = true;
            if (mdb.configured() && SystemClock.elapsedRealtime() >= mdbRetryAfter) try {
                String credential = mdb.load();
                String media = "series".equals(type) ? "show" : "movie";
                data = RatingData.parse(get("https://api.mdblist.com/imdb/" + media + "/" + id + "/?apikey=" + credential, null));
                success = true;
            } catch (StatusFailure failure) {
                failed = true; cooldown = Math.max(cooldown, failure.delay);
                if (requestedGeneration == generation) mdbRetryAfter = SystemClock.elapsedRealtime() + failure.delay;
            } catch (Exception ignored) {
                failed = true; if (requestedGeneration == generation) mdbRetryAfter = SystemClock.elapsedRealtime() + 60_000L;
            }
            if (tmdb.configured() && SystemClock.elapsedRealtime() >= tmdbRetryAfter) try {
                String credential = tmdb.load();
                boolean apiKey = credential.matches("[0-9A-Fa-f]{32}");
                String authorization = apiKey ? null : credential;
                String apiQuery = apiKey ? "&api_key=" + credential : "";
                JSONObject found = get("https://api.themoviedb.org/3/find/" + id + "?external_source=imdb_id" + apiQuery, authorization);
                String media = "series".equals(type) ? "tv" : "movie";
                JSONArray results = found.optJSONArray("series".equals(type) ? "tv_results" : "movie_results");
                JSONObject item = results == null ? null : results.optJSONObject(0);
                if (item != null && item.optLong("id") > 0) {
                    long reviews = -1;
                    try {
                        JSONObject reviewData = get("https://api.themoviedb.org/3/" + media + "/" + item.optLong("id") + "/reviews?language=en-US&page=1" + apiQuery, authorization);
                        reviews = reviewData.optLong("total_results", -1);
                    } catch (StatusFailure failure) {
                        failed = true; cooldown = Math.max(cooldown, failure.delay);
                        if (requestedGeneration == generation) tmdbRetryAfter = SystemClock.elapsedRealtime() + failure.delay;
                    } catch (Exception ignored) { failed = true; }
                    data.tmdb(item, reviews);
                }
                success = true;
            } catch (StatusFailure failure) {
                failed = true; cooldown = Math.max(cooldown, failure.delay);
                if (requestedGeneration == generation) tmdbRetryAfter = SystemClock.elapsedRealtime() + failure.delay;
            } catch (Exception ignored) {
                failed = true; if (requestedGeneration == generation) tmdbRetryAfter = SystemClock.elapsedRealtime() + 60_000L;
            }
            RatingData result = data;
            boolean good = success, partialFailure = failed;
            long wait = cooldown;
            main.post(() -> {
                if (closed || requestedGeneration != generation) return;
                pending.remove(identity);
                if (good) {
                    cache.put(identity, new Cached(result, SystemClock.elapsedRealtime(), partialFailure ? 5 * 60_000L : TTL_MS));
                    listener.ready(result);
                } else retryAfter = SystemClock.elapsedRealtime() + wait;
            });
        });
    }
    private static final class StatusFailure extends Exception {
        final long delay;
        StatusFailure(long delay) { this.delay = delay; }
    }
    private static JSONObject get(String address, String bearer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(4000); connection.setReadTimeout(7000);
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                long delay = 60_000L;
                if (status == 429) {
                    try { delay = Math.max(delay, Math.min(24L * 60 * 60_000L, Long.parseLong(connection.getHeaderField("Retry-After")) * 1000L)); }
                    catch (Exception ignored) { delay = 60 * 60_000L; }
                }
                throw new StatusFailure(delay);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int count;
                while ((count = in.read(buf)) != -1) { if (out.size() + count > 1_000_000) throw new Exception(); out.write(buf, 0, count); }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    void close() { closed = true; generation++; worker.shutdownNow(); pending.clear(); cache.clear(); }
}
