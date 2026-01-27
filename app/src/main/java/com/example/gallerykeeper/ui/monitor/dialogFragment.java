package com.example.gallerykeeper.ui.monitor;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.example.gallerykeeper.R;

public class dialogFragment extends DialogFragment {

    @Override

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_dialog, container, false);
        ScrollView scrollView = view.findViewById(R.id.infoScrollView);
        TextView textView = view.findViewById(R.id.infoTextView);

        // Définir le texte (le même que dans WelcomeFragment)
        textView.setText(getString(R.string.info_application_text));

        return view;
    }
}