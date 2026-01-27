package com.example.gallerykeeper;

import android.os.Bundle;
import android.content.SharedPreferences;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import com.example.gallerykeeper.ui.monitor.MonitorPhotoFragment;
import com.example.gallerykeeper.ui.register.LoginFragment;
import com.example.gallerykeeper.ui.welcome.WelcomeFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = getSharedPreferences("AppPrefs", 0);
        boolean hasSeenWelcome = preferences.getBoolean("hasSeenWelcome", false);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (!hasSeenWelcome) {
            transaction.replace(R.id.fragment_container, new WelcomeFragment());
        } else {
            transaction.replace(R.id.fragment_container, new MonitorPhotoFragment());
        }
        transaction.commit();
       /**if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new LoginFragment());
            transaction.commit();
        }*/
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

}