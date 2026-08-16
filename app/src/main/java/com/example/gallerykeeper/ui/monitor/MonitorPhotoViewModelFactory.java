package com.example.gallerykeeper.ui.monitor;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

public class MonitorPhotoViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;

    public MonitorPhotoViewModelFactory(Context context) {
        this.context = context.getApplicationContext(); // Utiliser le contexte d'application pour éviter les fuites
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MonitorPhotoViewModel.class)) {
            ViewModel vm = new MonitorPhotoViewModel(context);
            return Objects.requireNonNull(modelClass.cast(vm));
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
