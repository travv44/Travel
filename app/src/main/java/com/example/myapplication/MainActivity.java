package com.example.myapplication;

import android.os.Bundle;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_EXPLORE = "tab_explore";
    private static final String TAG_MAP = "tab_map";
    private static final String TAG_FAVORITES = "tab_favorites";
    private static final String TAG_PROFILE = "tab_profile";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Если пользователь не авторизован — отправляем на экран входа
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        // Если email/password и email не подтверждён — тоже на вход
        boolean hasPasswordProvider = false;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                hasPasswordProvider = true;
                break;
            }
        }
        if (hasPasswordProvider && !user.isEmailVerified()) {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("hint", "Подтвердите email и выполните вход.");
            startActivity(i);
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_explore);
        } else {
            int selected = binding.bottomNav.getSelectedItemId();
            if (selected == 0) {
                selected = R.id.nav_explore;
            }
            showTab(selected);
        }
    }

    private void showTab(int itemId) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);

        Fragment explore = fm.findFragmentByTag(TAG_EXPLORE);
        Fragment map = fm.findFragmentByTag(TAG_MAP);
        Fragment favorites = fm.findFragmentByTag(TAG_FAVORITES);
        Fragment profile = fm.findFragmentByTag(TAG_PROFILE);

        if (explore != null) {
            tx.hide(explore);
        }
        if (map != null) {
            tx.hide(map);
        }
        if (favorites != null) {
            tx.hide(favorites);
        }
        if (profile != null) {
            tx.hide(profile);
        }

        Fragment toShow;
        String tag;
        if (itemId == R.id.nav_explore) {
            tag = TAG_EXPLORE;
            toShow = explore != null ? explore : new ExploreFragment();
        } else if (itemId == R.id.nav_map) {
            tag = TAG_MAP;
            toShow = map != null ? map : new MapScreenFragment();
        } else if (itemId == R.id.nav_favorites) {
            tag = TAG_FAVORITES;
            toShow = favorites != null ? favorites : new FavoritesFragment();
        } else if (itemId == R.id.nav_profile) {
            tag = TAG_PROFILE;
            toShow = profile != null ? profile : new ProfileFragment();
        } else {
            return;
        }

        if (toShow.isAdded()) {
            tx.show(toShow);
        } else {
            tx.add(R.id.container, toShow, tag);
        }
        tx.commit();

        if (itemId == R.id.nav_favorites && toShow instanceof FavoritesFragment) {
            ((FavoritesFragment) toShow).refreshNow();
        }
        if (itemId == R.id.nav_profile && toShow instanceof ProfileFragment) {
            ((ProfileFragment) toShow).refreshNow();
        }
    }
}
