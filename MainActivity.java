package com.example.myapplication;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.myapplication.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            Fragment target = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_explore) {
                target = new ExploreFragment();
            } else if (itemId == R.id.nav_map) {
                target = new MapScreenFragment();
            } else if (itemId == R.id.nav_favorites) {
                target = new FavoritesFragment();
            } else if (itemId == R.id.nav_profile) {
                target = new ProfileFragment();
            }
            if (target != null) {
                openFragment(target);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_explore);
        }
    }

    private void openFragment(@NonNull Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.container, fragment);
        tx.commit();
    }
}