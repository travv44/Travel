package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.databinding.FragmentProfileBinding;
import com.example.myapplication.model.PlaceSuggestion;
import com.example.myapplication.utils.PlaceSearchHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirebaseDatabase firebaseDb;
    private DatabaseReference rootRef;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private PlaceSearchHelper placeSearchHelper;
    private java.util.List<PlaceSuggestion> currentSuggestions = new java.util.ArrayList<>();
    private PlaceSuggestion selectedFavPlace;
    private ValueEventListener favoriteTripsListener;
    private DatabaseReference favoriteTripsRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        firebaseDb = FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/");
        rootRef = firebaseDb.getReference();
        placeSearchHelper = new PlaceSearchHelper();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new android.content.Intent(requireContext(), AuthActivity.class));
            requireActivity().finish();
            return binding.getRoot();
        }

        // базовые данные
        binding.usernameText.setText(user.getEmail() != null ? user.getEmail() : "Пользователь");

        loadProfile(user.getUid());
        loadStats(user.getUid());
        setupFavoritePlaceSearch(user.getUid());

        binding.logoutButton.setOnClickListener(v -> signOutAll());

        return binding.getRoot();
    }

    public void refreshNow() {
        if (!isAdded() || binding == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            loadStats(user.getUid());
        }
    }

    private void loadProfile(String uid) {
        rootRef.child("users").child(uid).child("profile")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded() || binding == null) return;
                        String username = snapshot.child("username").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String favName = snapshot.child("favoritePlace").child("name").getValue(String.class);
                        String avatarKey = snapshot.child("avatarKey").getValue(String.class);

                        requireActivity().runOnUiThread(() -> {
                            if (username != null && !username.isEmpty()) {
                                binding.usernameText.setText(username);
                            } else {
                                binding.usernameText.setText(email != null ? email : "Пользователь");
                            }
                            binding.favPlaceSaved.setText("Текущее: " + (favName != null && !favName.isEmpty() ? favName : "—"));
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });

        // fallback: отдельный путь favoritePlace
        rootRef.child("users").child(uid).child("favoritePlace")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded() || binding == null) return;
                        String favName = snapshot.child("name").getValue(String.class);
                        if (favName != null && !favName.isEmpty()) {
                            requireActivity().runOnUiThread(() -> binding.favPlaceSaved.setText("Текущее: " + favName));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void loadStats(String uid) {
        // Избранные маршруты: users/{uid}/favoriteTrips
        if (favoriteTripsRef != null && favoriteTripsListener != null) {
            try {
                favoriteTripsRef.removeEventListener(favoriteTripsListener);
            } catch (Exception ignored) {
            }
        }
        favoriteTripsRef = rootRef.child("users").child(uid).child("favoriteTrips");
        favoriteTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long cnt = snapshot.getChildrenCount();
                if (!isAdded() || binding == null) return;
                requireActivity().runOnUiThread(() -> binding.favCountText.setText(String.valueOf(cnt)));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        favoriteTripsRef.addValueEventListener(favoriteTripsListener);

        // Создано маршрутов: считаем в Firebase /trips по userId
        rootRef.child("trips")
                .orderByChild("userId")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded() || binding == null) return;
                        requireActivity().runOnUiThread(() -> binding.tripsCountText.setText(String.valueOf(snapshot.getChildrenCount())));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void setupFavoritePlaceSearch(String uid) {
        AutoCompleteTextView input = binding.favPlaceInput;
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s == null || s.length() < 3) {
                    currentSuggestions.clear();
                    selectedFavPlace = null;
                    return;
                }
                String q = s.toString();
                executor.execute(() -> {
                    java.util.List<PlaceSuggestion> suggestions = placeSearchHelper.searchPlaces(q);
                    currentSuggestions = suggestions != null ? suggestions : new java.util.ArrayList<>();
                    if (!isAdded() || binding == null) return;
                    requireActivity().runOnUiThread(() -> {
                        java.util.List<String> display = new java.util.ArrayList<>();
                        for (PlaceSuggestion sug : currentSuggestions) {
                            String line = sug.getName();
                            if (sug.getDescription() != null && !sug.getDescription().isEmpty()) {
                                line += " - " + sug.getDescription();
                            }
                            display.add(line);
                        }
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, display);
                        input.setAdapter(adapter);
                        if (!display.isEmpty()) input.showDropDown();
                    });
                });
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        input.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentSuggestions.size()) {
                selectedFavPlace = currentSuggestions.get(position);
                input.setText(selectedFavPlace.getName());
            }
        });

        binding.saveFavPlaceButton.setOnClickListener(v -> {
            if (selectedFavPlace == null) {
                Toast.makeText(requireContext(), "Выберите место из подсказок", Toast.LENGTH_SHORT).show();
                return;
            }
            java.util.Map<String, Object> fav = new java.util.HashMap<>();
            fav.put("name", selectedFavPlace.getName());
            fav.put("lat", selectedFavPlace.getLat());
            fav.put("lon", selectedFavPlace.getLon());
            fav.put("updatedAt", System.currentTimeMillis());

            rootRef.child("users").child(uid).child("favoritePlace")
                    .setValue(fav)
                    .addOnSuccessListener(aVoid -> {
                        if (!isAdded() || binding == null) return;
                        binding.favPlaceSaved.setText("Текущее: " + selectedFavPlace.getName());
                        Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(requireContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    private void signOutAll() {
        FirebaseAuth.getInstance().signOut();
        try {
            int id = requireContext().getResources().getIdentifier("default_web_client_id", "string", requireContext().getPackageName());
            String webClientId = id != 0 ? getString(id) : null;
            if (webClientId != null && !webClientId.trim().isEmpty()) {
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build();
                GoogleSignInClient client = GoogleSignIn.getClient(requireContext(), gso);
                client.signOut();
            }
        } catch (Exception ignored) { }

        Toast.makeText(requireContext(), "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show();
        startActivity(new android.content.Intent(requireContext(), AuthActivity.class));
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (favoriteTripsRef != null && favoriteTripsListener != null) {
            try {
                favoriteTripsRef.removeEventListener(favoriteTripsListener);
            } catch (Exception ignored) {
            }
        }
        favoriteTripsListener = null;
        favoriteTripsRef = null;
        binding = null;
    }
}

