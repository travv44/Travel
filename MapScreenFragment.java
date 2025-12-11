package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.databinding.FragmentMapBinding;

public class MapScreenFragment extends Fragment {

    private FragmentMapBinding binding;
    private static final String JS_API_KEY = "34afc545-e0c4-4be9-a15c-5372f2b85691";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        setupWebView();
        loadMap();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.mapWebView.destroy();
        }
        binding = null;
    }

    private void setupWebView() {
        WebView webView = binding.mapWebView;
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
    }

    private void loadMap() {
        // Simple HTML that initializes Yandex Maps JS API and centers on Moscow
        String html = "<!DOCTYPE html>" +
                "<html><head><meta name='viewport' content='initial-scale=1.0, width=device-width'/>" +
                "<script src='https://api-maps.yandex.ru/2.1/?apikey=" + JS_API_KEY + "&lang=ru_RU' type='text/javascript'></script>" +
                "<style>html, body, #map { width:100%; height:100%; margin:0; padding:0; }</style>" +
                "</head><body>" +
                "<div id='map'></div>" +
                "<script type='text/javascript'>" +
                "ymaps.ready(function() {" +
                "  var map = new ymaps.Map('map', {center:[55.751244,37.618423], zoom:12, controls:['zoomControl','geolocationControl','searchControl']});" +
                "});" +
                "</script>" +
                "</body></html>";
        binding.mapWebView.loadDataWithBaseURL("https://api-maps.yandex.ru/", html, "text/html", "UTF-8", null);
    }
}

