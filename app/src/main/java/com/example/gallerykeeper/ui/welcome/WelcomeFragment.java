package com.example.gallerykeeper.ui.welcome;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.content.SharedPreferences;
import android.widget.TextView;

import com.example.gallerykeeper.R;
import com.example.gallerykeeper.ui.monitor.MonitorPhotoFragment;
import com.example.gallerykeeper.ui.register.LoginFragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WelcomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WelcomeFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private ScrollView scrollView;
    private Button okButton;
    private TextView longTextView; // Référence au TextView contenant le long texte

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public WelcomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment WelcomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static WelcomeFragment newInstance(String param1, String param2) {
        WelcomeFragment fragment = new WelcomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);

        scrollView = view.findViewById(R.id.scrollView);
        okButton = view.findViewById(R.id.okButton);
        longTextView = view.findViewById(R.id.welcomeText); // Assure-toi d'avoir un TextView avec cet ID dans ton layout


        // Désactiver le bouton "OK" par défaut
        setButtonState(okButton, false);

        // Appliquer l'apparence "non lu" au TextView/ScrollView au début
        // (selon si tu appliques le fond gris au TextView ou au ScrollView)
        // Si tu appliques le fond gris au TextView :
        // setTextViewBackground(longTextView, true);
        // Si tu appliques le fond gris au ScrollView (comme dans l'exemple XML précédent) :
        setScrollViewAndTextAppearance(scrollView, longTextView, true);


        // Ajouter un écouteur pour détecter le défilement
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // Vérifier si l'utilisateur a atteint le bas du ScrollView
            int scrollThreshold = 10; // Marge en pixels
            if (longTextView.getMeasuredHeight() <= scrollView.getHeight() + scrollY + scrollThreshold) {
                setButtonState(okButton, true); // Activer le bouton "OK"
                // Changer l'apparence du TextView/ScrollView pour indiquer qu'il est lu
                // Si tu appliques le fond gris au TextView :
                // setTextViewBackground(longTextView, false);
                // Si tu appliques le fond gris au ScrollView :
                setScrollViewAndTextAppearance(scrollView, longTextView, false);
            } else {
                setButtonState(okButton, false); // Désactiver le bouton "OK"
                // Restaurer l'apparence "non lu" si l'utilisateur remonte
                // Si tu appliques le fond gris au TextView :
                // setTextViewBackground(longTextView, true);
                // Si tu appliques le fond gris au ScrollView :
                setScrollViewAndTextAppearance(scrollView, longTextView, true);
            }
        });

        // *** AJOUTER CETTE VÉRIFICATION APRÈS AVOIR INITIALISÉ LES VUES ET L'ÉCOUTEUR ***
        // Vérifier si le contenu est plus court que le ScrollView au début
        // Il faut attendre que le layout soit mesuré pour obtenir les hauteurs correctes
        scrollView.post(() -> {
            if (longTextView.getMeasuredHeight() <= scrollView.getHeight()) {
                setButtonState(okButton, true); // Activer le bouton "OK" immédiatement
                // Changer l'apparence du TextView/ScrollView pour indiquer qu'il est lu
                // Si tu appliques le fond gris au TextView :
                // setTextViewBackground(longTextView, false);
                // Si tu appliques le fond gris au ScrollView :
                setScrollViewAndTextAppearance(scrollView, longTextView, false);
            }
        });

        // Action du bouton "OK"
        okButton.setOnClickListener(v -> {
            // Enregistrer que l'utilisateur a vu cette page
            SharedPreferences preferences = requireActivity().getSharedPreferences("AppPrefs", 0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("hasSeenWelcome", true);
            editor.apply();

            // Passer au fragment Login
            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new MonitorPhotoFragment()); // Remplace LoginFragment() par ton fragment de connexion
            transaction.commit();
        });

        return view;
    }

    // Méthode pour définir l'état (activé/désactivé) du bouton avec un effet visuel
    private void setButtonState(Button button, boolean isEnabled) {
        button.setEnabled(isEnabled);
        if (!isEnabled) {
            // Appliquer un effet visuel pour l'état désactivé (par exemple, grisé)
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0); // Applique un effet de désaturation (grayscale)
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
            button.getBackground().setColorFilter(filter); // Applique le filtre au fond du bouton

            // Optionnel : Changer la couleur du texte pour qu'elle corresponde mieux à l'état désactivé
            // button.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            // Supprimer l'effet visuel pour l'état activé
            button.getBackground().clearColorFilter(); // Supprime l'effet
            // Optionnel : Restaurer la couleur originale du texte si tu l'as changée
            // button.setTextColor(originalButtonTextColor);
        }
    }

    // Assure-toi d'avoir une méthode setScrollViewAndTextAppearance si tu suis l'exemple du ScrollView
    private void setScrollViewAndTextAppearance(ScrollView scrollView, TextView textView, boolean applyGrayBackground) {
        if (applyGrayBackground) {
            scrollView.setBackgroundColor(getResources().getColor(R.color.light_gray)); // Utilise ta couleur grise définie
            textView.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            scrollView.setBackgroundColor(getResources().getColor(R.color.white)); // Ou ta couleur de fond originale du ScrollView
            textView.setTextColor(getResources().getColor(R.color.black)); // Restaurer la couleur originale du texte
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nettoyer les références aux vues pour éviter les fuites de mémoire
        scrollView = null;
        okButton = null;
        longTextView = null;
    }
}