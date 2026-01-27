package com.example.gallerykeeper.ui.monitor;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class MonitorPhotoViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;

    public MonitorPhotoViewModelFactory(Context context) {
        this.context = context.getApplicationContext(); // Utiliser le contexte d'application pour éviter les fuites
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MonitorPhotoViewModel.class)) {
            return (T) new MonitorPhotoViewModel(context);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
