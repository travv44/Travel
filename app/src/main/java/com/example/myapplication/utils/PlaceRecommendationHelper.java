package com.example.myapplication.utils;

import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.RecommendedPlace;
import com.example.myapplication.model.VisitRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Простой персональный ранжировщик без ML: «центр интересов» по координатам истории
 * + бонус за совпадение слов из названий направлений с адресом/названием места.
 */
public final class PlaceRecommendationHelper {

    private static final double DEFAULT_LAT = 55.751244;
    private static final double DEFAULT_LON = 37.618423;
    /** ~50 км: локальный контекст для гео-скоринга */
    private static final double GEO_SCALE_KM = 50.0;

    private PlaceRecommendationHelper() {
    }

    public static List<RecommendedPlace> build(List<VisitRecord> visits,
                                                 List<EntertainmentPlace> catalog,
                                                 int limit) {
        if (catalog == null || catalog.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        List<VisitRecord> withCoords = new ArrayList<>();
        for (VisitRecord v : visits) {
            if (v != null && v.hasValidCoordinates()) {
                withCoords.add(v);
            }
        }

        boolean hasPersonalCenter = !withCoords.isEmpty();
        double centerLat;
        double centerLon;
        if (hasPersonalCenter) {
            double slat = 0;
            double slon = 0;
            for (VisitRecord v : withCoords) {
                slat += v.getLatAsDouble();
                slon += v.getLonAsDouble();
            }
            centerLat = slat / withCoords.size();
            centerLon = slon / withCoords.size();
        } else {
            centerLat = DEFAULT_LAT;
            centerLon = DEFAULT_LON;
        }

        Set<String> keywords = extractKeywords(visits);

        List<Scored> scored = new ArrayList<>();
        for (EntertainmentPlace p : catalog) {
            if (p == null || p.getId() == null) {
                continue;
            }
            double plat = parseDouble(p.getLat());
            double plon = parseDouble(p.getLon());
            if (Double.isNaN(plat) || Double.isNaN(plon)) {
                continue;
            }
            double distKm = haversineKm(centerLat, centerLon, plat, plon);
            double geoScore = hasPersonalCenter
                    ? 1.0 / (1.0 + distKm / GEO_SCALE_KM)
                    : 0.35;
            double kw = keywordBonus(p, keywords);
            double total = geoScore + kw;
            String reason = buildReason(hasPersonalCenter, distKm, kw > 0.05);
            scored.add(new Scored(p, total, reason));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<RecommendedPlace> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Scored s : scored) {
            if (seen.add(s.place.getId())) {
                out.add(new RecommendedPlace(s.place, s.reason));
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static String buildReason(boolean hasPersonalCenter, double distKm, boolean keywordHit) {
        if (keywordHit && hasPersonalCenter) {
            return "Совпадает с вашими направлениями и рядом с ними на карте";
        }
        if (keywordHit) {
            return "Похоже на ваши интересы по названиям городов";
        }
        if (hasPersonalCenter) {
            if (distKm < 80) {
                return "Недалеко от мест, которые вы открывали";
            }
            return "В том же регионе, что и ваши поездки";
        }
        return "Популярное место из каталога";
    }

    private static double keywordBonus(EntertainmentPlace p, Set<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        String hay = ((p.getName() != null ? p.getName() : "") + " "
                + (p.getAddress() != null ? p.getAddress() : "") + " "
                + (p.getDescription() != null ? p.getDescription() : ""))
                .toLowerCase(Locale.ROOT);
        double bonus = 0;
        for (String kw : keywords) {
            if (kw.length() >= 3 && hay.contains(kw)) {
                bonus += 0.35;
            }
        }
        return Math.min(bonus, 1.0);
    }

    private static Set<String> extractKeywords(List<VisitRecord> visits) {
        Set<String> out = new HashSet<>();
        if (visits == null) {
            return out;
        }
        for (VisitRecord v : visits) {
            if (v == null || v.getDestination() == null) {
                continue;
            }
            String dest = v.getDestination().trim().toLowerCase(Locale.ROOT);
            if (dest.isEmpty()) {
                continue;
            }
            String[] parts = dest.split("[,;\\|/]");
            for (String part : parts) {
                String token = part.trim();
                if (token.length() >= 3) {
                    out.add(token);
                }
                for (String w : token.split("\\s+")) {
                    if (w.length() >= 3) {
                        out.add(w);
                    }
                }
            }
        }
        return out;
    }

    private static double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static final class Scored {
        final EntertainmentPlace place;
        final double score;
        final String reason;

        Scored(EntertainmentPlace place, double score, String reason) {
            this.place = place;
            this.score = score;
            this.reason = reason;
        }
    }
}
