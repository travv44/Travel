package com.example.myapplication.utils;

import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.RecommendedPlace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Склеивает «рядом с вами» (API) и персональный каталог Firebase без дублей.
 */
public final class RecommendationMergeHelper {

    private RecommendationMergeHelper() {
    }

    public static List<RecommendedPlace> mergeNearbyFirst(List<EntertainmentPlace> nearby,
                                                          List<RecommendedPlace> personalized,
                                                          double userLat,
                                                          double userLon,
                                                          int maxTotal) {
        List<RecommendedPlace> merged = new ArrayList<>();
        if (maxTotal <= 0) {
            return merged;
        }
        Set<String> keys = new HashSet<>();

        int nearbyCap = Math.min(10, maxTotal);
        if (nearby != null) {
            for (EntertainmentPlace p : nearby) {
                if (merged.size() >= nearbyCap) {
                    break;
                }
                if (p == null) {
                    continue;
                }
                String key = dedupeKey(p);
                if (!keys.add(key)) {
                    continue;
                }
                double km = distanceKm(p, userLat, userLon);
                merged.add(new RecommendedPlace(p, "Рядом с вами", Double.isNaN(km) ? null : km));
            }
        }

        if (personalized != null) {
            for (RecommendedPlace rp : personalized) {
                if (merged.size() >= maxTotal) {
                    break;
                }
                if (rp == null || rp.getPlace() == null) {
                    continue;
                }
                if (keys.add(dedupeKey(rp.getPlace()))) {
                    merged.add(rp);
                }
            }
        }

        return merged;
    }

    private static String dedupeKey(EntertainmentPlace p) {
        String name = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT).trim() : "";
        String lat = p.getLat() != null ? p.getLat() : "";
        String lon = p.getLon() != null ? p.getLon() : "";
        return name + "|" + lat + "|" + lon;
    }

    private static double distanceKm(EntertainmentPlace p, double ulat, double ulon) {
        try {
            double plat = Double.parseDouble(p.getLat().trim().replace(',', '.'));
            double plon = Double.parseDouble(p.getLon().trim().replace(',', '.'));
            return haversineKm(ulat, ulon, plat, plon);
        } catch (Exception e) {
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

}
