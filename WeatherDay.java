package com.example.myapplication.model;

/**
 * Один день прогноза Open-Meteo.
 */
public class WeatherDay {
    private final String dateIso;
    private final int tempMinC;
    private final int tempMaxC;
    private final String summaryRu;

    public WeatherDay(String dateIso, int tempMinC, int tempMaxC, String summaryRu) {
        this.dateIso = dateIso;
        this.tempMinC = tempMinC;
        this.tempMaxC = tempMaxC;
        this.summaryRu = summaryRu;
    }

    public String getDateIso() {
        return dateIso;
    }

    public int getTempMinC() {
        return tempMinC;
    }

    public int getTempMaxC() {
        return tempMaxC;
    }

    public String getSummaryRu() {
        return summaryRu;
    }
}
