package com.example.myapplication.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.myapplication.model.EntertainmentPlace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * "Чем заняться" (быстро и понятно):
 * - один источник: OpenTripMap
 * - берём список xid по radius
 * - параллельно грузим details
 * - возвращаем только места с РЕАЛЬНЫМИ фото (preview.source)
 * - останавливаемся, когда собрали нужное количество
 */
public class ThingsToDoRepository {

    private static final String TAG = "ThingsToDoRepo";
    private static final String BASE = "https://api.opentripmap.com/0.1/en/places/";
    private static final String META_KEY = "opentripmap.apikey";

    private final OkHttpClient client;
    private final ExecutorService ioPool;
    private final String apiKey;

    public interface Callback {
        void onSuccess(List<EntertainmentPlace> places);
        void onError(String message);
    }

    public ThingsToDoRepository(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build();
        this.ioPool = Executors.newFixedThreadPool(6);
        this.apiKey = readApiKey(context);
    }

    public void loadTopWithPhotos(double lat, double lon, int desiredCount, Callback callback) {
        ioPool.execute(() -> {
            if (!hasKey()) {
                if (callback != null) callback.onError("Укажите ключ OpenTripMap (opentripmap.apikey) в AndroidManifest.xml");
                return;
            }
            try {
                // Progressive radius: usually enough to find 10 with photos.
                int[] radii = new int[]{20000, 30000, 45000, 60000, 80000};
                int[] listLimits = new int[]{120, 180, 220, 260, 320};
                List<EntertainmentPlace> best = new ArrayList<>();

                for (int step = 0; step < radii.length; step++) {
                    List<String> xids = fetchRadiusXids(lat, lon, radii[step], listLimits[step]);
                    List<EntertainmentPlace> found = fetchDetailsWithPhotos(lat, lon, xids, desiredCount);
                    if (found.size() > best.size()) best = found;
                    if (best.size() >= desiredCount) break;
                }

                // Sort by distance and cut
                Collections.sort(best, (a, b) -> Double.compare(distanceKmSafe(lat, lon, a), distanceKmSafe(lat, lon, b)));
                if (best.size() > desiredCount) best = new ArrayList<>(best.subList(0, desiredCount));

                if (callback != null) callback.onSuccess(best);
            } catch (Exception e) {
                Log.e(TAG, "loadTopWithPhotos failed", e);
                if (callback != null) callback.onError("Не удалось загрузить места: " + e.getMessage());
            }
        });
    }

    private List<String> fetchRadiusXids(double lat, double lon, int radiusMeters, int limit) throws Exception {
        // kinds are broad; photo filter happens on details stage.
        String kinds = "interesting_places,cultural,architecture,historic,museums,foods,amusements,natural,parks";
        String url = BASE + "radius"
                + "?radius=" + radiusMeters
                + "&lon=" + lon
                + "&lat=" + lat
                + "&kinds=" + URLEncoder.encode(kinds, StandardCharsets.UTF_8.name())
                + "&limit=" + limit
                + "&format=json"
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());

        JSONArray arr = httpJsonArray(url);
        if (arr == null) return new ArrayList<>();
        Set<String> uniq = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String xid = o.optString("xid", "").trim();
            if (!xid.isEmpty()) uniq.add(xid);
        }
        return new ArrayList<>(uniq);
    }

    private List<EntertainmentPlace> fetchDetailsWithPhotos(double lat, double lon, List<String> xids, int desiredCount) throws Exception {
        if (xids == null || xids.isEmpty()) return new ArrayList<>();

        // Submit detail calls concurrently; stop early when enough collected.
        CompletionService<EntertainmentPlace> cs = new ExecutorCompletionService<>(ioPool);
        List<Future<EntertainmentPlace>> futures = new ArrayList<>();

        int submitN = Math.min(xids.size(), 220); // cap to avoid spamming
        for (int i = 0; i < submitN; i++) {
            String xid = xids.get(i);
            futures.add(cs.submit(new DetailTask(xid)));
        }

        List<EntertainmentPlace> out = new ArrayList<>();
        for (int i = 0; i < submitN; i++) {
            Future<EntertainmentPlace> f = cs.take();
            EntertainmentPlace p = null;
            try {
                p = f.get(14, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            if (p != null && isRealPhotoUrl(p.getImageUrl())) {
                out.add(p);
                if (out.size() >= desiredCount) {
                    break;
                }
            }
        }

        // Cancel remaining
        for (Future<EntertainmentPlace> f : futures) {
            if (!f.isDone()) {
                try {
                    f.cancel(true);
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private final class DetailTask implements Callable<EntertainmentPlace> {
        private final String xid;

        DetailTask(String xid) {
            this.xid = xid;
        }

        @Override
        public EntertainmentPlace call() {
            return fetchDetails(xid);
        }
    }

    private EntertainmentPlace fetchDetails(String xid) {
        try {
            String url = BASE + "xid/" + URLEncoder.encode(xid, StandardCharsets.UTF_8.name())
                    + "?apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
            JSONObject root = httpJsonObject(url);
            if (root == null) return null;

            String name = root.optString("name", "").trim();
            if (name.isEmpty()) return null;

            JSONObject point = root.optJSONObject("point");
            if (point == null) return null;
            double plat = point.optDouble("lat", 0);
            double plon = point.optDouble("lon", 0);
            if (plat == 0 && plon == 0) return null;

            String imageUrl = null;
            JSONObject preview = root.optJSONObject("preview");
            if (preview != null) {
                imageUrl = preview.optString("source", null);
            }
            if (!isRealPhotoUrl(imageUrl)) return null;

            String desc = "";
            JSONObject wikipediaExtracts = root.optJSONObject("wikipedia_extracts");
            if (wikipediaExtracts != null) {
                desc = wikipediaExtracts.optString("text", "");
            }

            String address = "";
            JSONObject addr = root.optJSONObject("address");
            if (addr != null) {
                String road = addr.optString("road", "");
                String house = addr.optString("house_number", "");
                String city = addr.optString("city", "");
                StringBuilder sb = new StringBuilder();
                if (!road.isEmpty()) sb.append(road);
                if (!house.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(house);
                }
                if (!city.isEmpty()) {
                    if (sb.length() > 0) sb.append(" — ");
                    sb.append(city);
                }
                address = sb.toString();
            }

            return new EntertainmentPlace(
                    "otm_" + xid,
                    name,
                    desc != null ? desc : "",
                    String.format(Locale.US, "%.6f", plat),
                    String.format(Locale.US, "%.6f", plon),
                    imageUrl,
                    address
            );
        } catch (Exception e) {
            return null;
        }
    }

    private JSONArray httpJsonArray(String url) {
        try {
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return new JSONArray(resp.body().string());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject httpJsonObject(String url) {
        try {
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return new JSONObject(resp.body().string());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRealPhotoUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.ROOT);
        if (!u.startsWith("http")) return false;
        return true;
    }

    private double distanceKmSafe(double centerLat, double centerLon, EntertainmentPlace p) {
        try {
            if (p == null || p.getLat() == null || p.getLon() == null) return 1e9;
            double la = Double.parseDouble(p.getLat().replace(',', '.').trim());
            double lo = Double.parseDouble(p.getLon().replace(',', '.').trim());
            return haversineKm(centerLat, centerLon, la, lo);
        } catch (Exception e) {
            return 1e9;
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty() && !"PUT_YOUR_OPENTRIPMAP_KEY_HERE".equals(apiKey.trim());
    }

    private String readApiKey(Context context) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            if (ai.metaData == null) return null;
            Object v = ai.metaData.get(META_KEY);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

