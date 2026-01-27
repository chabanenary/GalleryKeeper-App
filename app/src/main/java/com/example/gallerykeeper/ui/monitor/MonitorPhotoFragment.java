package com.example.gallerykeeper.ui.monitor;

import android.Manifest;
import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.gallerykeeper.R;
import com.example.gallerykeeper.Utils.ImageData;
import com.example.gallerykeeper.Utils.PhotoFolderManager;
import com.example.gallerykeeper.Utils.PhotoPredictor;
import com.example.gallerykeeper.Utils.PhotoTaskUtils;
import com.example.gallerykeeper.Utils.CopyResult;
import com.example.gallerykeeper.Utils.UserPrefs;
import com.example.gallerykeeper.data.database.entities.Tag;
import com.example.gallerykeeper.data.database.AppDatabase;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import android.view.KeyEvent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MonitorPhotoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MonitorPhotoFragment extends Fragment implements MonitorPhotoViewModel.MonitoringCallback {

    private static final String TAG = "MonitorPhotoFragment"; // Pour le logging
    private MonitorPhotoViewModel viewModel; // Instance du ViewModel
    // \[1\] Champ pour mémoriser l'email utilisateur
    private String userEmail;

    // Executor pour exécuter les opérations de base de données en arrière-plan
    private Button scanButton;
    private Button monitorButton;
    private Button closeButton;
    private CheckBox checkNude, checkChildren, checkCreditCard, checkIdentityDocument;
    private ExecutorService databaseExecutor;

    // Pour gérer les permissions
    private ActivityResultLauncher<String[]> permissionsResultLauncher;
    private int deniedPermissionCount = 0;
    private ArrayList<String> permissionsList = new ArrayList<>();


    private ActivityResultLauncher<IntentSenderRequest> renameFolderIntentSenderLauncher;
    private List<Uri> urisToModifyForRename; // Pour stocker les URIs en attente de permission
    private String newRelativePathForRenameAttempt; // Pour stocker le nouveau chemin en attente


    // NOUVEAU launcher pour la suppression/modification d'images individuelles
    private ActivityResultLauncher<IntentSenderRequest> mediaModificationPermissionLauncher; // Nom distinct

    private Queue<Uri> pendingUrisForModification = new LinkedList<>();
    private BroadcastReceiver pendingDeletionReceiver;
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_FOR_DELETE = 102; // Code de requête pour WRITE_EXTERNAL_STORAGE

    private PhotoPredictor scan_predictor;


    private volatile boolean restoringState = false; // Flag pour ignorer les callbacks lors de la restauration silencieuse


    public MonitorPhotoFragment(){
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static MonitorPhotoFragment newInstance(String param1, String param2) {
        MonitorPhotoFragment fragment = new MonitorPhotoFragment();
        Bundle args = new Bundle();
        // args.putString(ARG_PARAM1, param1);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialise le ViewModel SANS factory personnalisée
        // ViewModelProvider utilisera une factory par défaut qui peut instancier
        // les ViewModels avec des constructeurs vides.
        //viewModel = new ViewModelProvider(this).get(MonitorPhotoViewModel.class);

        // Initialise l'ExecutorService de la database
        databaseExecutor = Executors.newSingleThreadExecutor();
        // Initialiser une seule fois Firebase pour récupérer l'email utilisateur
        // Remplacez ceci par l'email de l'utilisateur connecté
        // userEmail ="testmail";

      //  FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
       // if (currentUser != null && currentUser.getEmail() != null) {
       //     userEmail = currentUser.getEmail();
       //     Log.d(TAG, "Utilisateur connecté: " + userEmail);
        //} else {
        //    userEmail = null;
        //    Log.e(TAG, "Aucun utilisateur connecté ou email non disponible.");
        //}


        // \[1\] Récupérer l'email depuis les SharedPreferences
        Context appContext = requireContext().getApplicationContext();

        //userEmail = UserPrefs.getUserEmail(appContext);

        userEmail = "testmail"; // À supprimer après les tests
        UserPrefs.setUserEmail(appContext, userEmail);

        if (userEmail != null) {
            Log.d(TAG, "Utilisateur (prefs) connecté: " + userEmail);
        } else {
            Log.e(TAG, "Aucun userEmail trouvé dans UserPrefs.");
        }

        registerPendingDeletionReceiver(appContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view =  inflater.inflate(R.layout.fragment_monitor_photo, container, false);

        // Vérifie les permissions nécessaires pour Android 11 et supérieur
        Log.d("createMonitorPhotoView", "build version:  " + Build.VERSION.SDK_INT );
        if (Build.VERSION.SDK_INT > 33) {
            // Pour Android 14 et supérieur, on utilise les nouvelles permissions
            permissionsList.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissionsList.add(Manifest.permission.FOREGROUND_SERVICE);
            // Utiliser la chaîne littérale au lieu de la constante (disponible uniquement API 35)
            permissionsList.add("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING");
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT > 32) {
            permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES);

        } else {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }


        return view;
    }
    /**
     * This method is called when the view is created.
     * It sets up the click listener for the settings button.
     *
     * @param view The view created by onCreateView.
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        // Initialisation des vues
        scanButton = view.findViewById(R.id.scanButton);
        monitorButton = view.findViewById(R.id.monitorButton);
        closeButton = view.findViewById(R.id.closeButton);

        checkNude = view.findViewById(R.id.checkNude);
        checkChildren = view.findViewById(R.id.checkChildren);
        checkCreditCard = view.findViewById(R.id.checkCreditCard);
        checkIdentityDocument = view.findViewById(R.id.checkIdentityDocument);


        ImageButton settingsButtonNude = view.findViewById(R.id.settingsButtonNude);
        ImageButton settingsButtonChildren = view.findViewById(R.id.settingsButtonChildren);
        ImageButton settingsButtonCreditCard = view.findViewById(R.id.settingsButtonCreditCard);
        ImageButton settingsButtonIdentityDocument = view.findViewById(R.id.settingsButtonIdentityDocument);

        ImageButton settingsButtonInfoApp = view.findViewById(R.id.settingsButtonInfoApp);
        // Désactiver les CheckBox si les permissions ne sont pas accordées

        // Liste des CheckBox
        List<CheckBox> checkboxes = Arrays.asList(checkNude, checkChildren, checkCreditCard, checkIdentityDocument);

        // désactiver les checkbox au démarrage
        for (CheckBox checkbox : checkboxes) {
            checkbox.setEnabled(false);
        }

        // Crée une instance de la factory avec le contexte
        MonitorPhotoViewModelFactory factory = new MonitorPhotoViewModelFactory(requireContext());

        // Initialise le ViewModel avec la factory personnalisée
        viewModel = new ViewModelProvider(this, factory).get(MonitorPhotoViewModel.class);

        // Enregistrer les permissions et gérer les CheckBox
        registerActivityForMultiplePermissions(checkboxes);


        // Passez le fragment comme callback
        viewModel.setMonitoringCallback(this);

        // (Ancienne lecture prefs déplacée plus bas pour que les observers soient déjà en place)

        // Vérifiez les permissions et demandez-les si nécessaire
        if (!hasPermission()) {
            permissionsResultLauncher.launch(permissionsList.toArray(new String[0]));
        } else {
            for (CheckBox checkbox : checkboxes) {
                checkbox.setEnabled(true);
            }
            restoreTagsFromDatabase();
        }

        initializeRenameFolderLauncher();
        initializeDeleteMediaLauncher();

        // Observer les LiveData du ViewModel pour mettre à jour l'état des boutons.
        viewModel.getIsScanning().observe(getViewLifecycleOwner(), isScanning -> {
            scanButton.setText(isScanning ? getString(R.string.scan_stop_button_text) : getString(R.string.scan_button_text));
            monitorButton.setEnabled(!isScanning);
            monitorButton.setAlpha(isScanning ? 0.5f : 1.0f);
            boolean disableCheckboxes = isScanning || Boolean.TRUE.equals(viewModel.getIsMonitoring().getValue());
            checkNude.setEnabled(!disableCheckboxes);
            checkChildren.setEnabled(!disableCheckboxes);
            checkCreditCard.setEnabled(!disableCheckboxes);
            checkIdentityDocument.setEnabled(!disableCheckboxes);
            // sync settings buttons state
            settingsButtonNude.setEnabled(checkNude.isEnabled());
            settingsButtonNude.setAlpha(checkNude.isEnabled()?1f:0.5f);
            settingsButtonChildren.setEnabled(checkChildren.isEnabled());
            settingsButtonChildren.setAlpha(checkChildren.isEnabled()?1f:0.5f);
            settingsButtonCreditCard.setEnabled(checkCreditCard.isEnabled());
            settingsButtonCreditCard.setAlpha(checkCreditCard.isEnabled()?1f:0.5f);
            settingsButtonIdentityDocument.setEnabled(checkIdentityDocument.isEnabled());
            settingsButtonIdentityDocument.setAlpha(checkIdentityDocument.isEnabled()?1f:0.5f);
        });

        viewModel.getIsMonitoring().observe(getViewLifecycleOwner(), isMonitoring -> {
            monitorButton.setText(isMonitoring ? getString(R.string.monitor_stop_button_text): getString(R.string.monitor_button_text));
            scanButton.setEnabled(!isMonitoring);
            scanButton.setAlpha(isMonitoring ? 0.5f : 1.0f);
            boolean disableCheckboxes = isMonitoring || Boolean.TRUE.equals(viewModel.getIsScanning().getValue());
            checkNude.setEnabled(!disableCheckboxes);
            checkChildren.setEnabled(!disableCheckboxes);
            checkCreditCard.setEnabled(!disableCheckboxes);
            checkIdentityDocument.setEnabled(!disableCheckboxes);
            // sync settings buttons state
            settingsButtonNude.setEnabled(checkNude.isEnabled());
            settingsButtonNude.setAlpha(checkNude.isEnabled()?1f:0.5f);
            settingsButtonChildren.setEnabled(checkChildren.isEnabled());
            settingsButtonChildren.setAlpha(checkChildren.isEnabled()?1f:0.5f);
            settingsButtonCreditCard.setEnabled(checkCreditCard.isEnabled());
            settingsButtonCreditCard.setAlpha(checkCreditCard.isEnabled()?1f:0.5f);
            settingsButtonIdentityDocument.setEnabled(checkIdentityDocument.isEnabled());
            settingsButtonIdentityDocument.setAlpha(checkIdentityDocument.isEnabled()?1f:0.5f);
        });

        viewModel.isScanButtonEnabled().observe(getViewLifecycleOwner(), isEnabled -> {
            // Ne pas réactiver si monitoring actif (priorité à l'état monitoring)
            boolean monitoring = Boolean.TRUE.equals(viewModel.getIsMonitoring().getValue());
            boolean finalEnabled = isEnabled && !monitoring;
            scanButton.setEnabled(finalEnabled);
            scanButton.setAlpha(finalEnabled ? 1.0f : 0.5f);
        });

        viewModel.isMonitorButtonEnabled().observe(getViewLifecycleOwner(), isEnabled -> {
            monitorButton.setEnabled(isEnabled && !Boolean.TRUE.equals(viewModel.getIsScanning().getValue()));
            monitorButton.setAlpha(monitorButton.isEnabled() ? 1.0f : 0.5f);
        });

        // NOUVEL OBSERVER CENTRALISE POUR LES CHECKBOXES
        viewModel.areCheckboxesDisabled().observe(getViewLifecycleOwner(), disabled -> {
            boolean enable = !disabled; // activées seulement si false
            checkNude.setEnabled(enable && hasPermission());
            checkChildren.setEnabled(enable && hasPermission());
            checkCreditCard.setEnabled(enable && hasPermission());
            checkIdentityDocument.setEnabled(enable && hasPermission());
            // sync settings buttons state
            settingsButtonNude.setEnabled(checkNude.isEnabled());
            settingsButtonNude.setAlpha(checkNude.isEnabled()?1f:0.5f);
            settingsButtonChildren.setEnabled(checkChildren.isEnabled());
            settingsButtonChildren.setAlpha(checkChildren.isEnabled()?1f:0.5f);
            settingsButtonCreditCard.setEnabled(checkCreditCard.isEnabled());
            settingsButtonCreditCard.setAlpha(checkCreditCard.isEnabled()?1f:0.5f);
            settingsButtonIdentityDocument.setEnabled(checkIdentityDocument.isEnabled());
            settingsButtonIdentityDocument.setAlpha(checkIdentityDocument.isEnabled()?1f:0.5f);
        });

        // === Application tardive de l'état monitoring (après installation observers) ===
        try {
            SharedPreferences sp = requireContext().getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE);
            boolean active = sp.getBoolean("active", false);
            if (active) {
                Log.d(TAG, "Service monitoring actif détecté au démarrage: désactivation bouton Scan");
            }
            viewModel.applyMonitoringStateFromService(active);
        } catch (Exception e) {
            Log.w(TAG, "Lecture état monitoring (post observers) échouée", e);
        }

        // Définir les listeners pour les boutons qui appellent les méthodes du ViewModel


        scanButton.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.getIsScanning().getValue())) {
                viewModel.setContext(requireContext());
                showStopMonitoringConfirmationDialog(getString(R.string.action_scan), viewModel::stopScanning);
            } else {
                viewModel.setContext(requireContext());
                showMonitoringConfirmationDialog(getString(R.string.action_scan), viewModel::startScanning);
            }
        });

        monitorButton.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.getIsMonitoring().getValue())) {
                // viewModel.setContext(requireContext());
                // showStopMonitoringConfirmationDialog("Monitor", viewModel::stopMonitoring);
                // Stopper réellement le service de monitoring
                showStopMonitoringConfirmationDialog(getString(R.string.action_monitor), () -> stopMonitoringService(requireContext()));

            } else {
                viewModel.setContext(requireContext());
                showMonitoringConfirmationDialog(getString(R.string.action_monitor), viewModel::startMonitoring);
            }
        });

        // Listener commun pour la logique de base (mise à jour du ViewModel)
        // Ce listener sera toujours appelé, que la case soit cochée ou décochée.
        View.OnClickListener baseCheckboxClickListener = v -> updateViewModelOnCheckboxChange();

        checkNude.setOnClickListener(v -> {
            if (restoringState) return; // Ignorer si restauration
            if (checkNude.isChecked()) {

                Log.d(TAG, "Checkbox Nude cochée");
                // Créer un Map pour les données du tag "nu"
                Map<String, Object> tagData = new HashMap<>();
                tagData.put("tagName", "Nude");
                tagData.put("folderName", "Nude_photos");
                tagData.put("monitored", false);
                tagData.put("scanned", false);
                // Appeler la méthode pour insérer le tag "nu" dans la base de données
                insertTagIntoDatabase(tagData);
                // Afficher une boîte de dialogue de confirmation pour le tag "nu"
                showTagConfirmationDialog("Nude");

            }

            // Mettre à jour l'état des boutons Scan/Monitor
            updateViewModelOnCheckboxChange(); // Mettre à jour l'état des boutons Scan/Monitor
        });

        checkNude.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                Log.d(TAG, "Checkbox Nude décochée");
                // Logique spécifique au décochage
                uncheckSpecificCheckbox("Nude");
            }
        });

        checkChildren.setOnClickListener(v -> {
            if (restoringState) return;
            if (checkChildren.isChecked()) {

                // Créer un Map pour les données du tag "nu"
                Map<String, Object> tagData = new HashMap<>();
                tagData.put("tagName", "Children");
                tagData.put("folderName", "Children_photos");
                tagData.put("monitored", false);
                tagData.put("scanned", false);
                // Appeler la méthode pour insérer le tag "Children" dans la base de données
                insertTagIntoDatabase(tagData);
                // Afficher une boîte de dialogue de confirmation pour le tag "Children"
                showTagConfirmationDialog("Children");
            }
            updateViewModelOnCheckboxChange();
        });
        checkChildren.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                Log.d(TAG, "Checkbox Children décochée");
                // Logique spécifique au décochage
                uncheckSpecificCheckbox("Children");
            }
        });

        checkCreditCard.setOnClickListener(v -> {
            if (restoringState) return;
            if (checkCreditCard.isChecked()) {

                // Créer un Map pour les données du tag "nu"
                Map<String, Object> tagData = new HashMap<>();
                tagData.put("tagName", "Credit Card");
                tagData.put("folderName", "CreditCard_photos");
                tagData.put("monitored", false);
                tagData.put("scanned", false);
                // Appeler la méthode pour insérer le tag "Credit Card" dans la base de données
                insertTagIntoDatabase(tagData);
                // Afficher une boîte de dialogue de confirmation pour le tag "Credit card"

                showTagConfirmationDialog("Credit Card");
                // Vérifier les permissions avant de créer le dossier photo

            }
            updateViewModelOnCheckboxChange();
        });

        checkCreditCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                Log.d(TAG, "Checkbox Credit Card décochée");
                // Logique spécifique au décochage
                uncheckSpecificCheckbox("Credit Card");
            }
        });

        checkIdentityDocument.setOnClickListener(v -> {
            if (restoringState) return;
            if (checkIdentityDocument.isChecked()) {

                // Créer un Map pour les données du tag "nu"
                Map<String, Object> tagData = new HashMap<>();
                tagData.put("tagName", "Identity Document");
                tagData.put("folderName", "Ids_photos");
                tagData.put("monitored", false);
                tagData.put("scanned", false);
                // Appeler la méthode pour insérer le tag "Identity Document" dans la base de données
                insertTagIntoDatabase(tagData);
                // Afficher une boîte de dialogue de confirmation pour le tag "Identity Document"

                showTagConfirmationDialog("Identity Document");
                // Vérifier les permissions avant de créer le dossier photo

            }
            updateViewModelOnCheckboxChange();
        });

        checkIdentityDocument.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                Log.d(TAG, "Checkbox Identity Document décochée");
                // Logique spécifique au décochage
                uncheckSpecificCheckbox("Identity Document");
            }
        });


        // Appel initial pour définir l'état des boutons en fonction des checkboxes
        updateViewModelOnCheckboxChange();


        // Listeners pour les ImageButtons (PopupMenu)
        settingsButtonNude.setOnClickListener(v -> showPopupMenu(v, "Nude"));
        settingsButtonChildren.setOnClickListener(v -> showPopupMenu(v, "Children"));
        settingsButtonCreditCard.setOnClickListener(v -> showPopupMenu(v, "Credit Card"));
        settingsButtonIdentityDocument.setOnClickListener(v -> showPopupMenu(v, "Identity Document"));



        //Listeners pour le bouton "Fermer"
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Bouton Fermer cliqué.");

           // FirebaseAuth.getInstance().signOut();


            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        //Listeners pour le bouton "info"
        settingsButtonInfoApp.setOnClickListener(v -> {
            dialogFragment infoDialog = new dialogFragment();
            infoDialog.show(getParentFragmentManager(), "InfoDialog");
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        pullPendingDeletionsFromRepository();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Context context = getContext();
        if (context != null && pendingDeletionReceiver != null) {
            context.getApplicationContext().unregisterReceiver(pendingDeletionReceiver);
            pendingDeletionReceiver = null;
        }
        super.onDestroy();
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
    }

    /**
     * Affiche un PopupMenu pour les paramètres du tag.
     * @param view La vue sur laquelle le PopupMenu sera ancré.
     * @param tagName Le nom du tag pour lequel le menu est affiché.
     */

    private void showPopupMenu(View view, String tagName) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.tags_settings_menu, popupMenu.getMenu());


        // Forcer l'affichage des icônes (nécessaire pour certaines versions/thèmes)
        // Cela peut être nécessaire si ton thème ne les affiche pas par défaut.
        // Nécessite une réflexion, donc à utiliser avec précaution ou chercher des alternatives
        // si possible (comme un style de thème personnalisé pour PopupMenu).
        try {
            Field[] fields = popupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceShowIcon.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        // Charger les valeurs par défaut depuis la base de données
        if (userEmail == null) {
            Log.e(TAG, "userEmail nul, impossible d'ouvrir la base spécifique à l'utilisateur.");
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getContext().getApplicationContext(), userEmail);
                Tag tag = db.tagDao().getTagByName(tagName);

                if (tag != null) {
                    // Mettre à jour les titres du menu avec les valeurs de la base de données
                    popupMenu.getMenu().findItem(R.id.folder_name).setTitle(tag.getFolderName());
                }  } catch (Exception e) {
                Log.e(TAG, "Erreur lors du chargement des données du tag", e);
            }
        });

        popupMenu.setOnMenuItemClickListener(item -> {
            // Gérer les clics sur les items du menu
            // Vérifier si le tag est sélectionné (coché)
            boolean isTagChecked = false;
            if (tagName.equals("Nude")) {
                isTagChecked = checkNude.isChecked();
            } else if (tagName.equals("Children")) {
                isTagChecked = checkChildren.isChecked();
            } else if (tagName.equals("Credit Card")) {
                isTagChecked = checkCreditCard.isChecked();
            } else if (tagName.equals("Identity Document")) {
                isTagChecked = checkIdentityDocument.isChecked();
            }

            if (!isTagChecked) {
                Snackbar.make(getView(), "Veuillez d'abord sélectionner le tag \"" + tagName + "\".", Snackbar.LENGTH_LONG).show();
                return false; // Empêche l'action
            }

            String folderName = popupMenu.getMenu().findItem(R.id.folder_name).getTitle().toString();
            int itemId = item.getItemId();
            if (itemId == R.id.folder_name) {
                if (tagName != null) {// Action pour folder_name
                    showChangeFolderNameConfirmationDialog(tagName);
                    return true;
                }else {
                    Log.e(TAG, "Tag non trouvé pour le nom: " + tagName);
                    showInformationTagMessage(tagName);
                    return true;
                }

            } else {
                    Log.d(TAG, "No valid item selected");
                    showInformationTagMessage(tagName);
                    return true;
            }
        });

        popupMenu.show();
    }

    private String getUsername_Firebase() {

        // Récupérer l'email de l'utilisateur connecté
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            Log.d(TAG, "Utilisateur connecté: " + currentUser.getEmail());
            return(currentUser.getEmail());
        } else {
            Log.e(TAG, "Aucun utilisateur connecté ou email non disponible.");
            return null; // Ou une valeur par défaut si nécessaire
        }
    }
    private String getUsername() {
        return userEmail;
    }
    private void fillDatabase(Map<String, Object> tagData) {

        if (getContext() == null) {
            Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
                return;
        }

        if (userEmail == null) {
            Log.e(TAG, "userEmail nul, impossible d'ouvrir la base spécifique à l'utilisateur.");
            return;
        }
        // Obtenir l'instance de la base de données
        AppDatabase db = AppDatabase.getInstance(getContext().getApplicationContext(), userEmail);

        Tag tag = new Tag();
        tag.setAll(tagData); // Utiliser setAll pour définir les valeurs
        db.tagDao().insertTag(tag);
        //Utilisez une bibliothèque comme javax.crypto pour crypter les mots de passe avant de les insérer dans la base de données.
        // Logique ou action pour remplir la base de données
        Log.d(TAG, "Base de données remplie avec succès pour l'utilisateur: " + userEmail);
    }
    private void insertTagIntoDatabase(Map<String, Object> tagData) {

        // Récupérer l'email de l'utilisateur connecté
        // String userEmail = getUsername();
        // Remplacez ceci par l'email de l'utilisateur connecté
        //String userEmail ="testmail";


        // Exécute l'opération de base de données sur un thread d'arrière-plan
        databaseExecutor.execute(() -> {
            try {
                // Obtenir l'instance de la base de données
                // Le contexte est nécessaire ici, assure-toi que le fragment est attaché
                if (getContext() == null) {
                    Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
                    return;
                }
                fillDatabase(tagData);


                // Optionnel: Afficher un message de succès sur le thread UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) { // Vérifie à nouveau si le fragment est attaché
                            Toast.makeText(getContext(), getString(R.string.toast_tag_added_to_db), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Tag  inséré avec succès pour l'utilisateur: " + userEmail);
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'insertion du tag 'nu'", e);
                // Optionnel: Afficher un message d'erreur sur le thread UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            Log.d(TAG, "Erreur lors de l'ajout du tag.");
                        }
                    });
                }
            }
        });
    }


    // Méthode pour supprimer un tag de la base de données
    private void removeTagFromDatabase(String tagName) {
        if (getContext() == null) {
            Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
            return;
        }
        if (userEmail == null) {
            Log.e(TAG, "userEmail nul, impossible d'ouvrir la base spécifique à l'utilisateur.");
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getContext().getApplicationContext(), userEmail);
                db.tagDao().deleteTagByName(tagName);
                Log.d(TAG, "Tag supprimé avec succès : " + tagName);
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la suppression du tag", e);
            }
        });
    }


    // Méthode pour supprimer un tag de la base de données
    private void removeAllTagsFromDatabase(String userMail) {
        if (getContext() == null) {
            Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getContext().getApplicationContext(), userMail);
                db.tagDao().deleteAllTags();
                Log.d(TAG, "Tous les Tag supprimé avec succès : ");
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la suppression des tags", e);
            }
        });
    }



    private void getFolderNameByTagName(String tagName, Consumer<String> onComplete) {
        if (getContext() == null || !isAdded()) {
            Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
            if (onComplete != null) {
                onComplete.accept(null); // Retourne null si le contexte est nul
            }
            return;
        }


        if (tagName == null || tagName.isEmpty()) {
            Log.e(TAG, "Le tagName est nul ou vide. Impossible de continuer.");
            if (onComplete != null) {
                onComplete.accept(null); // Retourne null si le tagName est invalide
            }
            return;
        }

        if (userEmail == null) {
            Log.e(TAG, "userEmail nul, impossible d'ouvrir la base spécifique à l'utilisateur.");
            return;
        }

        Log.d(TAG, "Recherche du folderName pour le tagName : " + tagName);

        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getContext(), userEmail);
                Tag tag = db.tagDao().getTagByName(tagName);
                String folderName = null;

                if (tag != null) {
                    folderName = tag.getFolderName();
                    Log.d(TAG, "FolderName récupéré pour le tag: " + tagName + " - " + folderName);
                } else {
                    Log.e(TAG, "Tag non trouvé pour le tagName: " + tagName);
                }

                // Exécuter le callback avec le folderName récupéré
                if (onComplete != null) {
                    String finalFolderName = folderName; // Nécessaire pour les lambdas
                    // getActivity().runOnUiThread(() -> onComplete.accept(finalFolderName));
                    // Vérifier si le Fragment est toujours dans un état valide pour recevoir des mises à jour UI
                    if (isAdded() && getActivity() != null && getView() != null) {
                        getActivity().runOnUiThread(() -> {
                            // Vérifier à nouveau ici si nécessaire, surtout si le callback fait des choses complexes
                            if (isAdded() && getView() != null) {
                                onComplete.accept(finalFolderName);
                            } else {
                                Log.w(TAG, "Fragment détaché ou vue nulle juste avant d'exécuter onComplete.accept().");
                            }
                        });
                    } else {
                        Log.w(TAG, "Fragment non ajouté, activité nulle, ou vue nulle. Impossible d'exécuter onComplete sur le UI thread.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la récupération du folderName", e);
                if (onComplete != null) {
                    getActivity().runOnUiThread(() -> onComplete.accept(null)); // Retourne null en cas d'erreur
                }
            }
        });
    }
    private void updateFolderNameInDatabase(String tagName, String folderName, BiConsumer<String, String> onComplete) {
        if (getContext() == null) {
            Log.e(TAG, "Contexte nul, impossible d'accéder à la base de données.");
            return;
        }

        if (userEmail == null) {
            Log.e(TAG, "userEmail nul, impossible d'ouvrir la base spécifique à l'utilisateur.");
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getContext().getApplicationContext(), userEmail);
                Tag tag = db.tagDao().getTagByName(tagName);
                String oldFolderName = null; // Variable pour stocker l'a
                if (tag != null) {
                    oldFolderName = tag.getFolderName();
                    tag.setFolderName(folderName);
                    db.tagDao().updateTag(tag);
                    Log.d(TAG, "FolderName mis à jour pour le tag: " + tagName +"with folderName: " + folderName);
                    // Exécuter le callback avec le folderName récupéré
                    if (onComplete != null) {
                        String finalFolderName = folderName; // Nécessaire pour les lambdas
                        String oldFinalFolderName = oldFolderName; // Nécessaire pour les lambdas
                        getActivity().runOnUiThread(() -> onComplete.accept(oldFinalFolderName, finalFolderName));
                    }
                } else {
                    Log.e(TAG, "Tag non trouvé pour le tagName: " + tagName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la mise à jour du folderName", e);
            }
        });
    }

    /**
     * Méthode pour décocher une checkbox spécifique en fonction de son tagName.
     * Cette méthode est appelée lorsque l'utilisateur souhaite décocher manuellement
     * une checkbox associée à un tag spécifique.
     *
     * @param tagName Le nom du tag associé à la checkbox à décocher.
     */
    private void uncheckSpecificCheckbox(String tagName) {
        if (tagName == null) return;

        // Assurez-vous que les vues sont initialisées
        if (checkNude == null || checkChildren == null || checkCreditCard == null || checkIdentityDocument == null) {
            Log.w(TAG, "Les checkboxes ne sont pas encore initialisées pour uncheckSpecificCheckbox.");
            return;
        }

        // Utilise les textes des checkboxes pour les identifier (ou des constantes si vous préférez)
        if (tagName.equals("Nude")) {
            checkNude.setChecked(false);
            Log.d(TAG," Tag Nu unchecked");
        } else if (tagName.equals("Children")) {
            checkChildren.setChecked(false);
            Log.d(TAG," Tag Children unchecked");

        } else if (tagName.equals("Credit Card")) {
            checkCreditCard.setChecked(false);
            Log.d(TAG," Tag Credit Card unchecked");
        } else if (tagName.equals("Identity Document")) {
            checkIdentityDocument.setChecked(false);
            Log.d(TAG," Tag Identity Document unchecked");
        }
        // Afficher une boîte de dialogue de confirmation pour la suppression ou le renommage du tag
        showDeleteOrRenameDialog(getContext(), tagName);
        // Important: Après avoir décoché manuellement, mettez à jour l'état des boutons Scan/Monitor
        updateViewModelOnCheckboxChange();
    }

    /**
     * Met à jour le ViewModel en fonction de l'état actuel des checkboxes.
     * Cette méthode est appelée chaque fois qu'une checkbox change d'état
     * et une fois lors de la création de la vue.
     */
    private void updateViewModelOnCheckboxChange() {
        if (viewModel == null || checkNude == null || checkChildren == null || checkCreditCard == null || checkIdentityDocument == null) {
            // Sécurité pour éviter les NullPointerExceptions si la méthode est appelée avant que tout soit initialisé
            // (bien que l'appel initial dans onViewCreated devrait garantir que les vues sont prêtes)
            return;
        }

        boolean isAnyCheckboxSelected = checkNude.isChecked() ||
                checkChildren.isChecked() ||
                checkCreditCard.isChecked() ||
                checkIdentityDocument.isChecked();

        /**
         * Récupère l'état actuel des checkboxes et notifie le ViewModel.
         * Le ViewModel déterminera ensuite si les boutons "Scan" et "Monitor"
         * doivent être activés (si une checkbox est cochée) ou désactivés et grisés (si aucune n'est cochée).
         */
        viewModel.onCheckboxSelectionChanged(isAnyCheckboxSelected);

        // Alternativement, si la logique est TRÈS simple et que tu ne veux pas
        // que le ViewModel s'en charge, tu pourrais directement activer/désactiver les boutons ici.
        // Mais il est préférable de laisser le ViewModel gérer l'état.
        // Exemple (moins recommandé pour une bonne architecture) :
        // scanButton.setEnabled(isAnyCheckboxSelected);
        // monitorButton.setEnabled(isAnyCheckboxSelected);
        // scanButton.setAlpha(isAnyCheckboxSelected ? 1.0f : 0.5f);
        // monitorButton.setAlpha(isAnyCheckboxSelected ? 1.0f : 0.5f);
    }

    //methode pour gestion des checkbox


    private void showTagConfirmationDialog(String tagName) {
        if (getContext() == null) {
            return; // Ne rien faire si le contexte n'est pas disponible
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.dialog_photo_folder)); // Titre de la boîte de dialogue
        builder.setMessage(getString(R.string.dialog_photo_folder_text1)+ " \" " + tagName + "_photos " + "\" "+ getString(R.string.dialog_photo_folder_text2));


        builder.setPositiveButton(getString(R.string.dialog_define_folder_name), (dialog, which) -> {
            // L'utilisateur a confirmé le tag, maintenant demander le nom du dossier
            dialog.dismiss(); // Fermer la première boîte de dialogue
            showEnterFolderNameDialog(tagName); // Ouvrir la deuxième boîte de dialogue
        });

        builder.setNegativeButton(getString(R.string.dialog_photo_folder_cancel), (dialog, which) -> {
            Log.d(TAG," Default folder name used for tag: " + tagName);
                    // uncheckSpecificCheckbox(tagName); // Utilise tagName ici aussi
            // Définir le nom du dossier photo avec le nom par défaut
            showFinalConfirmationMessage(tagName);
            dialog.dismiss();
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Méthode pour afficher la boîte de dialogue de saisie du nom de dossier
    private void showEnterFolderNameDialog(String tagName) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.dialog_define_folder_name_title) + " \" " + tagName + "\" ");
        builder.setMessage(getString(R.string.dialog_define_folder_name_text));

        // Créer un EditText pour la saisie de l'utilisateur
        final EditText inputFolderName = new EditText(requireContext());
        inputFolderName.setInputType(InputType.TYPE_CLASS_TEXT);
        inputFolderName.setHint(tagName+getString(R.string.dialog_define_folder_name_suggestion)); // Suggestion
        builder.setView(inputFolderName); // Ajouter l'EditText à la boîte de dialogue

        // Bouton "OK" (ou "Sauvegarder")
        builder.setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {
            String folderName = inputFolderName.getText().toString().trim();
            if (!folderName.isEmpty()) {
                dialog.dismiss(); // Fermer cette boîte de dialogue
                updateFolderNameInDatabase(tagName, folderName, (oldFolderName, newFolderName) -> {
                    showFinalConfirmationMessage(tagName);
                });
            } else {
                Toast.makeText(getContext(), getString(R.string.toast_folder_name_empty), Toast.LENGTH_SHORT).show();
                // Optionnel: Rappeler la même boîte de dialogue ou gérer l'erreur autrement
                showEnterFolderNameDialog(tagName); // Relance la saisie du nom de dossier
            }
            dialog.dismiss();
        });

        // Bouton "Annuler"
        builder.setNegativeButton(getString(R.string.dialog_define_folder_name_cancel), (dialog, which) -> {
            Toast.makeText(getContext(), getString(R.string.toast_folder_name_change_cancelled), Toast.LENGTH_SHORT).show();
            // Définir le nom du dossier photo avec le nom par défaut
            showFinalConfirmationMessage(tagName);
            dialog.dismiss();
        });

        builder.setCancelable(false); // Forcer un choix
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void showChangeFolderNameConfirmationDialog(String tagName) {
        if (getContext() == null) {
            return; // Ne rien faire si le contexte n'est pas disponible
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.dialog_photo_folder_name_change)); // Titre de la boîte de dialogue
        builder.setMessage(getString(R.string.dialog_photo_folder_name_change_text1)+ " \" " + tagName + "\" "+ getString(R.string.dialog_photo_folder_name_change_text2));


        builder.setPositiveButton(getString(R.string.dialog_define_folder_name), (dialog, which) -> {
            // L'utilisateur a confirmé le tag, maintenant demander le nom du dossier
            dialog.dismiss(); // Fermer la première boîte de dialogue
            showChangeFolderNameDialog(tagName); // Ouvrir la deuxième boîte de dialogue
        });

        builder.setNegativeButton(getString(R.string.dialog_photo_folder_cancel), (dialog, which) -> {
            Toast.makeText(getContext(), getString(R.string.toast_folder_name_unchanged_for_tag, tagName), Toast.LENGTH_SHORT).show();
            // uncheckSpecificCheckbox(tagName); // Utilise tagName ici aussi
            // Définir le nom du dossier photo avec le nom par défaut
            dialog.dismiss();
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void showChangeFolderNameDialog(String tagName) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.dialog_define_folder_name_title) + " \" " + tagName + "\" ");
        builder.setMessage(getString(R.string.dialog_define_folder_name_text));

        // Créer un EditText pour la saisie de l'utilisateur
        final EditText inputFolderName = new EditText(requireContext());
        inputFolderName.setInputType(InputType.TYPE_CLASS_TEXT);

        String hintName = tagName+getString(R.string.dialog_define_folder_name_suggestion); // suggestion
        inputFolderName.setHint(hintName);

// Ajoutez l'EditText dans votre AlertDialog comme d'habitude

        builder.setView(inputFolderName); // Ajouter l'EditText à la boîte de dialogue
        // Listener pour la touche Tabulation
        inputFolderName.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_TAB && event.getAction() == KeyEvent.ACTION_DOWN) {
                inputFolderName.setText(hintName);
                // Ici, déclencher l'action de renommage ou fermer le dialog
                // Par exemple : dialog.dismiss(); ou bouton positif.performClick();
                return true;
            }
            return false;
        });

        // Bouton "OK" (ou "Sauvegarder")
        builder.setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {
            String folderName = inputFolderName.getText().toString().trim();
            if (!folderName.isEmpty()) {
                dialog.dismiss();

                updateFolderNameInDatabase(tagName, folderName, (oldFolderName, newFolderName) -> {
                    showChangeFolderNameConfirmationInfo(tagName, oldFolderName, newFolderName);
                });
            } else {
                Toast.makeText(getContext(), getString(R.string.toast_folder_name_empty), Toast.LENGTH_SHORT).show();
                // Optionnel: Rappeler la même boîte de dialogue ou gérer l'erreur autrement
                showChangeFolderNameDialog(tagName); // Relance la saisie du nom de dossier
            }
            dialog.dismiss();
        });

        // Bouton "Annuler"
        builder.setNegativeButton(getString(R.string.dialog_define_folder_name_cancel), (dialog, which) -> {
            Toast.makeText(getContext(), getString(R.string.toast_folder_name_change_cancelled), Toast.LENGTH_SHORT).show();
            // Définir le nom du dossier photo avec le nom par défaut
            showFinalConfirmationMessage(tagName);
            dialog.dismiss();
        });

        builder.setCancelable(false); // Forcer un choix
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    //boite de dialogue du codepin pour le dossier

    private void showFinalConfirmationMessage(String tagName) {

        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_setup_complete))
                .setMessage(getString(R.string.dialog_message_proceed_scan_for_tag, tagName))
                .setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {
                    // Vérifier les permissions avant de créer le dossier photo

                    if (hasPermission()) {

                        getFolderNameByTagName(tagName, folderName -> {

                            if (folderName != null) {
                                Log.d(TAG, "Nom du dossier récupéré : " + folderName);

                                PhotoFolderManager.createPhotoFolderWithPlaceholder(getContext(), folderName, "image_principale.jpg");

                            } else {
                                Log.e(TAG, "Impossible de récupérer le nom du dossier pour le tag : " + tagName);
                            }
                        });

                    } else {
                        shouldShowPermissionRationaleIfNeeded();
                    }


                    dialog.dismiss();
                })
                .setCancelable(false) // Empêche la fermeture en cliquant à l'extérieur
                .show();

        // Si vous préférez un Toast au lieu d'un Snackbar, décommentez la ligne suivante :
        // Toast.makeText(getContext(), "Configuration terminée. Vous pouvez à présent procéder au scan des photos.", Toast.LENGTH_LONG).show();

    }

    private void showChangeFolderNameConfirmationInfo(String tagName, String oldFolderName, String newFolderName){

        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_folder_rename_done))
                .setMessage(getString(R.string.dialog_message_folder_rename_done, tagName, oldFolderName, newFolderName))
                .setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {

                    Log.d(TAG, "Renommage du dossier avec le nom : " + oldFolderName + "en " + newFolderName);
                    boolean success = false;

                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        success = PhotoFolderManager.renamePhotoFolderLegacy(getContext(), oldFolderName,newFolderName);
                    }else{
                        success = PhotoFolderManager.renamePhotoFolder(getContext(), oldFolderName,newFolderName);
                    }

                    if (success) {
                        Log.d(TAG, "Dossier renommé avec succès : " + newFolderName);
                    } else {
                        Log.e(TAG, "Échec du renommage du dossier : " + newFolderName);
                    }
                    dialog.dismiss();
                })
                .setCancelable(false) // Empêche la fermeture en cliquant à l'extérieur
                .show();
    }

    private void showInformationTagMessage( String tagName) {

        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_tag_not_selected))
                .setMessage(getString(R.string.dialog_message_select_tag_first, tagName))
                .setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false) // Empêche la fermeture en cliquant à l'extérieur
                .show();

        // Si vous préférez un Toast au lieu d'un Snackbar, décommentez la ligne suivante :
        // Toast.makeText(getContext(), "Configuration terminée. Vous pouvez à présent procéder au scan des photos.", Toast.LENGTH_LONG).show();
    }

    private void showMonitoringConfirmationDialog(String action, Runnable onConfirm) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_confirmation))
                .setMessage(getString(R.string.dialog_message_start_action, action))
                .setPositiveButton("Oui", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Non", null)
                .setPositiveButton(getString(R.string.yes_text), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.no_text), null)
                .show();
    }

    private void showStopMonitoringConfirmationDialog(String action, Runnable onConfirm) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_confirmation))
                .setMessage(getString(R.string.dialog_message_stop_action, action))
               .setPositiveButton("Oui", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Non", null)
                .setPositiveButton(getString(R.string.yes_text), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.no_text), null)
                .show();
    }


    // Méthode pour enregistrer les permissions nécessaires

    public void registerActivityForMultiplePermissions(List<CheckBox> checkboxes) {
        permissionsResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;

            for (Boolean isAllowed : result.values()) {
                if (!isAllowed) {
                    allGranted = false;
                    break;
                }
            }

            Log.d("registerActivityForMultiplePermissions", "permissions demandées: " + allGranted);

            // Activer ou désactiver les CheckBox en fonction des permissions
            for (CheckBox checkbox : checkboxes) {
                checkbox.setEnabled(allGranted);
            }

            if (allGranted) {
                Log.d("registerActivityForMultiplePermissions", "Toutes les permissions sont accordées");
                restoreTagsFromDatabase(); // Restauration silencieuse après obtention des permissions
            } else {

                Log.d("registerActivityForMultiplePermissions", "Certaines permissions sont refusées");
                deniedPermissionCount++;
                if (deniedPermissionCount < 2) {
                    shouldShowPermissionRationaleIfNeeded();
                } else {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
                    builder.setTitle(getString(R.string.dialog_title_permission_needed));
                    builder.setMessage(getString(R.string.dialog_message_permission_needed));
                    builder.setPositiveButton(getString(R.string.go_to_settings), (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    });
                    builder.setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss());
                    builder.create().show();
                }
            }
        });
    }


    public void shouldShowPermissionRationaleIfNeeded() {
        ArrayList<String> deniedPermissions = new ArrayList<>();

        for (String permission: permissionsList) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permission)) {
                deniedPermissions.add(permission);
            }

        }
        if(!deniedPermissions.isEmpty()){
            Snackbar.make(getView(), "Please grant necessary permissions to access photos", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Grant", v -> {
                        permissionsResultLauncher.launch(deniedPermissions.toArray(new String[0]));
                    }).show();
        }else{
            permissionsResultLauncher.launch(permissionsList.toArray(new String[0]));
        }

    }

    public boolean hasPermission(){
        for (String permission: permissionsList) {
            if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Appelez ceci dans onViewCreated (Fragment) ou onCreate (Activity)
    private void initializeRenameFolderLauncher() {
        renameFolderIntentSenderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "Permission accordée pour modifier les fichiers pour le renommage.");
                        // Réessayer de renommer les fichiers pour lesquels la permission a été demandée
                        if (urisToModifyForRename != null && !urisToModifyForRename.isEmpty() && newRelativePathForRenameAttempt != null) {
                            // Ici, vous devriez idéalement ne mettre à jour que les URIs pour lesquelles la permission a été accordée.
                            // Le résultat de createWriteRequest s'applique à toutes les URIs de la requête.
                            boolean allSucceeded = true;
                            for (Uri uriToUpdate : urisToModifyForRename) {
                                if (!PhotoFolderManager.updateSingleFileRelativePath(getContext(), uriToUpdate, newRelativePathForRenameAttempt)) {
                                    allSucceeded = false;
                                    Log.w(TAG, "Échec de la mise à jour du chemin pour " + uriToUpdate + " après l'octroi de la permission.");
                                }
                            }
                            if (allSucceeded) {
                               Toast.makeText(getContext(), "Dossier renommé avec succès (après permission).", Toast.LENGTH_SHORT).show();
                                Toast.makeText(getContext(), getString(R.string.toast_rename_success_after_permission), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Certains fichiers n'ont pas pu être déplacés (après permission).", Toast.LENGTH_SHORT).show();
                                Toast.makeText(getContext(), getString(R.string.toast_some_files_not_moved_after_permission), Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Log.w(TAG, "Permission refusée pour modifier les fichiers pour le renommage.");
                        Toast.makeText(getContext(), "Permission refusée pour renommer le dossier.", Toast.LENGTH_SHORT).show();
                        Toast.makeText(getContext(), getString(R.string.toast_permission_denied_rename_folder), Toast.LENGTH_SHORT).show();
                    }
                    // Nettoyer les variables d'état
                    urisToModifyForRename = null;
                    newRelativePathForRenameAttempt = null;
                });
    }

    private void initializeDeleteMediaLauncher() {
        // Initialisation du NOUVEAU launcher pour la modification/suppression de médias
        mediaModificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (pendingUrisForModification.isEmpty()) {
                        Log.w("MediaModify", "Callback du launcher appelé mais pendingImageUriForModification est null. Opération annulée ou déjà traitée.");
                        return; // Sortir tôt si l'URI en attente est null
                    }

                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // L'UTILISATEUR A APPROUVÉ LA SUPPRESSION VIA LA BOÎTE DE DIALOGUE SYSTÈME.
                        // LE SYSTÈME A (OU VA TRÈS PROCHAINEMENT) SUPPRIMER LE FICHIER
                        // À pendingImageUriForModification.
                        // VOUS NE DEVEZ PAS LE SUPPRIMER À NOUVEAU ICI.

                        // Nettoyer les données en attente

                        Uri uriProcessed = pendingUrisForModification.poll(); // Récupère et supprime l'élément en tête de file
                        Log.d("MediaModify", "Suppression de l'image originale approuvée par l'utilisateur et gérée par le système.");
                        // Ici, vous pouvez effectuer des actions qui dépendent du succès de l'opération globale
                        // (copie réussie ET suppression de l'original approuvée).
                        // Par exemple, mettre à jour l'UI, afficher un message de succès global, etc.
                        // Toast.makeText(getContext(), "Image déplacée avec succès.", Toast.LENGTH_SHORT).show();
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q){

                            if (pendingUrisForModification.isEmpty()){
                                Log.d("MediaModify", "Tous les URIs ont été traités pour la suppression.");
                            } else {
                                // Appeler à nouveau la méthode pour traiter le prochain URI
                                handleAndroidQDeleteRequest(getContext(), pendingUrisForModification);
                            }

                        }

                    } else {
                        // L'utilisateur a refusé la suppression de l'image originale, ou une autre erreur s'est produite
                        // lors de la demande de permission.

                        // À ce stade, l'image originale N'A PAS été supprimée par le système.
                        // Vous avez précédemment COPIÉ l'image dans un nouveau dossier.
                        // Vous devez décider quoi faire :
                        // Option 1: Laisser l'image copiée et l'originale (l'utilisateur n'a pas voulu supprimer l'original).
                        // Option 2: Annuler l'opération de "déplacement" en supprimant l'image que vous aviez COPIÉE.
                        //           Cela nécessiterait de connaître l'URI/chemin du fichier COPIÉ.
                        //           Exemple (si vous aviez stocké l'URI de la copie) :
                        //           if (pendingCopiedImageUri != null) {
                        //               Log.d("MediaModify", "Tentative de suppression de l'image copiée car l'original n'a pas été supprimé.");
                        //               PhotoFolderManager.deleteImageFromSource(getContext(), pendingCopiedImageUri); // Supprimer la copie
                        //           }
                        // Toast.makeText(getContext(), "L'image originale n'a pas été supprimée.", Toast.LENGTH_SHORT).show();
                        Log.w("MediaModify", "L'utilisateur a refusé la suppression de l'image originale ou une erreur s'est produite.");
                    }
                });
    }

    // Définir ce code de requête au niveau de la classe de votre Fragment/Activity
// public static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_FOR_DELETE = 102;
// Et une variable pour stocker l'URI si vous devez réessayer après la demande de permission
// private Uri pendingUriForLegacyDeleteAttempt = null;




    private void handleLegacyDeleteRequest(Context context, Queue<Uri> urisToDelete) {
        if (urisToDelete == null || urisToDelete.isEmpty()) {
            Log.d("MediaModify", "Aucun URI à supprimer.");
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            while (!urisToDelete.isEmpty()) {
                Uri imageUriToDelete = urisToDelete.peek(); // Récupère et retire l'élément en tête de la file
                Log.d("MediaModify", "Tentative de suppression directe pour URI: " + imageUriToDelete);

                boolean success = PhotoFolderManager.deleteImageFromSource(context, imageUriToDelete);

                if (success) {
                    Log.d("MediaModify", "Suppression réussie pour URI: " + imageUriToDelete);
                    urisToDelete.poll(); // Supprimer l'URI de la file d'attente après succès
                } else {
                    Log.e("MediaModify", "Échec de la suppression pour URI: " + imageUriToDelete);
                }
            }
            Log.d("MediaModify", "Traitement terminé pour tous les URIs.");
        } else {
            Log.w("MediaModify", "Permission WRITE_EXTERNAL_STORAGE nécessaire. Demande de la permission pour le premier URI.");
            Uri pendingUri = urisToDelete.peek(); // Récupère l'élément en tête sans le retirer
            if (pendingUri != null && context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_WRITE_EXTERNAL_STORAGE_FOR_DELETE);
            } else {
                Log.e("MediaModify", "Impossible de demander la permission. Contexte invalide ou file vide.");
            }
        }
    }






    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void handleAndroidQDeleteRequest(Context context, Queue<Uri> urisToDelete) {
        if (urisToDelete == null || urisToDelete.isEmpty()) {
            Log.d("MediaModify", "Android Q: La file d'attente des URIs à supprimer est vide ou nulle.");
            return;
        }

        // Process one URI at a time to handle permission requests correctly.
        Uri imageUriToDelete = urisToDelete.peek(); // Look at the next URI without removing it yet.

        if (imageUriToDelete == null) {
            Log.w("MediaModify", "Android Q: L'URI en tête de file est null, suppression de la file.");
            urisToDelete.poll(); // Remove the null URI
            // Try to process the next one if the queue isn't empty
            if (!urisToDelete.isEmpty()) {
                handleAndroidQDeleteRequest(context, urisToDelete);
            }
            return;
        }

        Log.d("MediaModify", "Android Q: Tentative de suppression pour URI: " + imageUriToDelete);
        try {
            int rowsDeleted = context.getContentResolver().delete(imageUriToDelete, null, null);

            if (rowsDeleted > 0) {
                Log.d("MediaModify", "Android Q: Suppression directe réussie pour URI: " + imageUriToDelete);
                urisToDelete.poll(); // Remove the successfully deleted URI from the queue
                // Process the next URI in the queue if any
                if (!urisToDelete.isEmpty()) {
                    handleAndroidQDeleteRequest(context, urisToDelete);
                } else {
                    Log.d("MediaModify", "Android Q: Toutes les URIs ont été traitées.");
                }
            } else {
                // No rows deleted, but no exception. This could mean the file didn't exist,
                // or the app has permission but the URI was invalid for deletion by this app.
                Log.w("MediaModify", "Android Q: Aucune image supprimée (delete a retourné 0) pour URI: " + imageUriToDelete + ". Cela peut être normal si l'URI n'était plus valide ou déjà supprimé.");
                urisToDelete.poll(); // Remove this URI as we can't do more with it directly.
                // Process the next URI
                if (!urisToDelete.isEmpty()) {
                    handleAndroidQDeleteRequest(context, urisToDelete);
                }
            }
        } catch (RecoverableSecurityException rse) {
            Log.w("MediaModify", "Android Q: RecoverableSecurityException pour URI: " + imageUriToDelete + ". Demande de permission.");
            // Store the URI that needs permission. You'll need a member variable for this.
            // this.pendingUriForQPermission = imageUriToDelete; // Make sure pendingUriForQPermission is declared at class level

            try {
                IntentSender sender = rse.getUserAction().getActionIntent().getIntentSender();
                // Ensure mediaModificationPermissionLauncher is initialized
                if (mediaModificationPermissionLauncher != null) {
                    // IMPORTANT: The ActivityResultLauncher must be properly set up to handle the result.
                    // After the user grants/denies permission, the launcher's callback will be invoked.
                    // In that callback, you should:
                    // 1. If granted: Retry deleting 'imageUriToDelete'.
                    // 2. If denied: Remove 'imageUriToDelete' from the queue (or mark as failed).
                    // 3. In either case, then call handleAndroidQDeleteRequest again to process the *next* item.

                    // For now, we are just launching. The actual re-processing logic after permission
                    // needs to be in the ActivityResultLauncher's callback.
                    // The current loop will EXIT after this launch because we are not polling from the queue yet.
                    // The idea is to wait for user input.

                    // Storing the URI that needs permission is crucial for the callback.
                    // Let's assume you have a way to link this specific URI to the callback.
                    // One way is to have a class-level variable like 'currentUriRequiringPermission'.
                    // For instance:
                    // currentUriRequiringPermission = imageUriToDelete;

                    IntentSenderRequest request = new IntentSenderRequest.Builder(sender).build();
                    mediaModificationPermissionLauncher.launch(request);
                    // DO NOT poll from urisToDelete here or continue the loop.
                    // Wait for the permission result. The result callback should handle the next step.
                } else {
                    Log.e("MediaModify", "Android Q: mediaModificationPermissionLauncher non initialisé ! Impossible de demander la permission pour " + imageUriToDelete);
                    // Could remove the problematic URI and try the next, or stop.
                    urisToDelete.poll(); // Remove URI we can't get permission for right now
                    if (!urisToDelete.isEmpty()) {
                        handleAndroidQDeleteRequest(context, urisToDelete);
                    }
                }
            } catch (Exception e_rse) {
                Log.e("MediaModify", "Android Q: Erreur lancement IntentSender pour " + imageUriToDelete + ": ", e_rse);
                urisToDelete.poll(); // Remove URI that caused an error during permission request
                if (!urisToDelete.isEmpty()) {
                    handleAndroidQDeleteRequest(context, urisToDelete);
                }
            }
        } catch (SecurityException se) {
            Log.e("MediaModify", "Android Q: SecurityException non récupérable pour URI " + imageUriToDelete + ": ", se);
            urisToDelete.poll(); // Remove problematic URI
            if (!urisToDelete.isEmpty()) {
                handleAndroidQDeleteRequest(context, urisToDelete);
            }
        } catch (Exception e) {
            Log.e("MediaModify", "Android Q: Erreur lors de la tentative de suppression pour URI " + imageUriToDelete + ": ", e);
            urisToDelete.poll(); // Remove problematic URI
            if (!urisToDelete.isEmpty()) {
                handleAndroidQDeleteRequest(context, urisToDelete);
            }
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void handleAndroidRPlusDeleteRequest(Context context, Queue<Uri> urisToDelete) {
        if (urisToDelete == null || urisToDelete.isEmpty()) {
            Log.w("MediaModify", "La file d'attente des URIs à supprimer est vide.");
            return;
        }

        try {
            // createDeleteRequest accepte n'importe quelle Collection, y compris une Queue
            IntentSender sender = MediaStore.createDeleteRequest(
                    context.getContentResolver(),
                    urisToDelete
            ).getIntentSender();

            new Handler(Looper.getMainLooper()).post(() -> {
                if (mediaModificationPermissionLauncher != null) {
                    Log.d("MediaModify", "Lancement de createDeleteRequest pour " + urisToDelete.size() + " URI(s) (Android R+)");
                    mediaModificationPermissionLauncher.launch(new IntentSenderRequest.Builder(sender).build());
                } else {
                    Log.e("MediaModify", "mediaModificationPermissionLauncher non initialisé ! (Android R+)");
                }
            });
        } catch (Exception e) {
            Log.e("MediaModify", "Erreur lors de la création de createDeleteRequest : ", e);
            // Gérer l'erreur, par exemple avec un Toast
        }
    }
    // Fonction pour demander la permission de modification/suppression

    private void requestModificationPermission(Context context, Queue<Uri> urisToDelete) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Logique pour Android 11 (API 30) et supérieur
            // Utilise MediaStore.createDeleteRequest
            handleAndroidRPlusDeleteRequest(context, urisToDelete);

        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Logique pour Android 10 (API 29)
            // Tente une suppression directe, attrape RecoverableSecurityException
            // handleAndroidQDeleteRequest(context, urisToDelete);
            handleLegacyDeleteRequest(context, urisToDelete);

        } else {
            // Logique pour Android 9 (API 28) et inférieur
            // Vérifie WRITE_EXTERNAL_STORAGE, tente une suppression directe
            // ou demande la permission.
            handleLegacyDeleteRequest(context, urisToDelete);
        }
    }



    @Override
    public void onMonitoringTask(Context context) {
        Log.d("MonitorPhotoFragment", "onMonitoringTask appelé dans le fragment avec le contexte.");
        // Démarrer le service de monitoring
        startMonitoringService(context);

    }
    public void startMonitoringService(Context context) {
        Intent serviceIntent = new Intent(context, MonitorForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }



    @Override
    public void onScanTask(Context context) {
        Log.d("MonitorPhotoFragment", "onScanTask appelé avec le contexte.");

        scan_predictor = new PhotoPredictor(context);
        processAllPhotosInGallery(context);

    }


    private void processAllPhotosInGallery(Context context){

        if (hasPermission()) {
            List<ImageData> allImages = PhotoFolderManager.loadImagesFromGallery(context);

            if (!allImages.isEmpty()) {
                int count = 0;
                // Process the images
                for (ImageData image : allImages) {
                    // Do something with each Bitmap (e.g., display it in an ImageView)
                    Uri imageUri = image.getUri(); // Récupère l'URI de l'image
                    Bitmap imageBitmap = image.getBitmap(); // Récupère le Bitmap de l'image
                    if (imageUri != null && imageBitmap != null) {

                        Log.d("Scan Task", "Processing image: " + imageUri.toString());
                        String tagName = scan_predictor.predict(imageBitmap); // Appel de la méthode pour prédire l'image

                        if (tagName != null) {
                            String emailUser = getUsername();
                            Log.d("Scan Task", "emailUser = " + emailUser + " | tagName = " + tagName);
                            // Move the image to the tag folder
                            PhotoTaskUtils.moveUriToTagFolder(context, emailUser, imageUri, tagName, result -> {
                                // Ici, tu peux utiliser le résultat de la copie
                                if (result.isSuccess()) {
                                    // Succès
                                    // Stocker les infos pour le callback du launcher
                                    pendingUrisForModification.add(imageUri);

                                    Log.d("moveUriToTagFolder", "demande au systeme  suppression de l'uri: " + imageUri);
                                    requestModificationPermission(context,pendingUrisForModification);
                                } else {
                                    // Échec
                                    Log.e("PhotoFolderManager", "Échec de la copie de l'image.");
                                }
                            });

                        } else {
                            Log.d("Scan Task", "No relevant tag found for the image.");
                        }

                    } else {
                        Log.e("Scan Task", "Image URI or Bitmap is null for image at index: " + count);
                    }

                }
            } else {
                Log.d("Scan Task", "No images found in the gallery.");
            }

        }else {
            shouldShowPermissionRationaleIfNeeded();
        }
    }




    public interface CopyResultCallback {
        void onCopyResult(CopyResult result);
    }

    public void deleteFolderByTagName(Context context, String tagName) {


        if (hasPermission()) {

            getFolderNameByTagName(tagName, folderName -> {
                if (folderName != null) {
                    Log.d(TAG, "Nom du dossier à supprimer : " + folderName);

                    boolean success = PhotoFolderManager.deletePhotoFolder(context, folderName);

                    if (success) {
                        Log.d(TAG, "Dossier supprimé avec succès : " + folderName);
                        // Supprimer le tag de la base de données
                        removeTagFromDatabase(tagName);
                    } else {
                        Log.e(TAG, "Échec de la suppression du dossier : " + folderName);
                    }
                } else {
                    Log.e(TAG, "Impossible de récupérer le nom du dossier pour le tag : " + tagName);
                }
            });
        } else {
            shouldShowPermissionRationaleIfNeeded();
        }
    }

    public void renameFolderByTagName(Context context, String tagName, String newFolderName) {

        if (hasPermission()) {

            getFolderNameByTagName(tagName, folderName -> {
                if (folderName != null) {
                    Log.d(TAG, "Nom du dossier à renommer : " + folderName);
                    boolean success = false;

                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        success = PhotoFolderManager.renamePhotoFolderLegacy(context, folderName,newFolderName);
                    }else{
                        success = PhotoFolderManager.renamePhotoFolder(context, folderName,newFolderName);
                    }

                    if (success) {
                        Log.d(TAG, "Dossier renommé avec succès : " + folderName);

                        // Supprimer le tag de la base de données
                        removeTagFromDatabase(tagName);
                    } else {
                        Log.e(TAG, "Échec du renommage du dossier : " + folderName);
                    }
                } else {
                    Log.e(TAG, "Impossible de récupérer le nom du dossier pour le tag : " + tagName);
                }
            });


        } else {
            shouldShowPermissionRationaleIfNeeded();
        }

    }

    // Affiche la boîte de dialogue principale
    private void showDeleteOrRenameDialog(Context context, String tagName) {
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.dialog_title_remove_monitoring))
                .setMessage(getString(R.string.dialog_message_remove_monitoring, tagName))
                .setPositiveButton(getString(R.string.delete_text), (dialog, which) -> showConfirmDeleteDialog(context, tagName))
                .setNegativeButton(getString(R.string.rename_text), (dialog, which) -> showRenameDialog(context, tagName))
                .show();
    }

    // Confirmation de suppression
    private void showConfirmDeleteDialog(Context context, String tagName) {
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.dialog_title_confirm_delete))
                .setMessage(getString(R.string.dialog_message_confirm_delete, tagName))
                .setPositiveButton(getString(R.string.yes_delete), (dialog, which) -> deleteFolderByTagName(context, tagName))
                .setNegativeButton(getString(R.string.dialog_define_folder_name_cancel), (dialog, which) -> showDeleteOrRenameDialog(context, tagName))
                .show();
    }

    // Appel de la boîte de dialogue de renommage

    private void showRenameDialog(Context context, String tagName){
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.rename_folder_for_tag_title, tagName));
        builder.setMessage(getString(R.string.dialog_define_folder_name_text));

        // Créer un EditText pour la saisie de l'utilisateur
        final EditText inputFolderName = new EditText(requireContext());
        inputFolderName.setInputType(InputType.TYPE_CLASS_TEXT);
        // Ajout de la date à la suggestion
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());

        String hintName = tagName + "_pict_" + dateStr; // suggestion
        inputFolderName.setHint(hintName);

// Ajoutez l'EditText dans votre AlertDialog comme d'habitude

        builder.setView(inputFolderName); // Ajouter l'EditText à la boîte de dialogue
        // Listener pour la touche Tabulation
        inputFolderName.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_TAB && event.getAction() == KeyEvent.ACTION_DOWN) {
                inputFolderName.setText(hintName);
                // Ici, déclencher l'action de renommage ou fermer le dialog
                // Par exemple : dialog.dismiss(); ou bouton positif.performClick();
                return true;
            }
            return false;
        });


        // Bouton "OK" (ou "Sauvegarder")
        builder.setPositiveButton(getString(R.string.ok_button_text), (dialog, which) -> {
            String folderName = inputFolderName.getText().toString().trim();
            if (!folderName.isEmpty()) {
                dialog.dismiss();
                // Appeler la méthode pour renommer le dossier
                Log.d(TAG, "Renommage du dossier avec le nom : " + folderName);
                // Appeler la méthode pour renommer le dossier
                renameFolderByTagName(context, tagName, folderName);

            } else {
                Toast.makeText(getContext(), getString(R.string.toast_folder_name_empty), Toast.LENGTH_SHORT).show();
                // Optionnel: Rappeler la même boîte de dialogue ou gérer l'erreur autrement
                showRenameDialog(context, tagName); // Relance la saisie du nom de dossier
            }
            dialog.dismiss();
        });

        // Bouton "Annuler"
        builder.setNegativeButton(getString(R.string.dialog_define_folder_name_cancel), (dialog, which) -> dialog.dismiss());


        builder.setCancelable(false); // Forcer un choix
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void stopMonitoringService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, MonitorForegroundService.class);
            boolean stopped = context.stopService(serviceIntent);
            Log.d(TAG, "stopService appelé, stopped=" + stopped);
            // Optionnel: mise à jour immédiate UI, sinon le broadcast fera foi
            viewModel.applyMonitoringStateFromService(false);
        } catch (Exception e) {
            Log.w(TAG, "Arrêt du service monitoring a échoué", e);
        }
    }

    private void registerPendingDeletionReceiver(Context appContext) {
        if (pendingDeletionReceiver != null) {
            return;
        }
        IntentFilter filter = new IntentFilter(PendingDeletionRepository.ACTION_PENDING_DELETIONS_UPDATED);
        pendingDeletionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                pullPendingDeletionsFromRepository();
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(pendingDeletionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                appContext.registerReceiver(pendingDeletionReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Impossible d'enregistrer le receiver des suppressions en attente", e);
            pendingDeletionReceiver = null;
        }
    }

    private void pullPendingDeletionsFromRepository() {
        if (!isAdded()) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        List<Uri> persisted = PendingDeletionRepository.drain(appContext);
        if (persisted.isEmpty()) {
            return;
        }
        if (!isResumed()) {
            PendingDeletionRepository.prependAll(appContext, persisted);
            return;
        }
        Log.d(TAG, "Récupération de " + persisted.size() + " URI(s) en attente depuis le dépôt.");
        pendingUrisForModification.addAll(persisted);
        requestModificationPermission(requireContext(), pendingUrisForModification);
    }

    private void restoreTagsFromDatabase() {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment détaché : impossible de restaurer les tags.");
            return;
        }

        if (userEmail == null) {
            Log.e(TAG, "restoreTagsFromDatabase: userEmail nul, impossible de restaurer.");
            return;
        }

        restoringState = true;
        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext(), userEmail);
                List<Tag> tags = db.tagDao().getAllTags();
                if (tags == null || tags.isEmpty()) {
                    restoringState = false;
                    return;
                }
                Map<String, Boolean> states = new HashMap<>();
                for (Tag tag : tags) {
                    states.put(tag.getTagName(), true);
                }
                requireActivity().runOnUiThread(() -> applyRestoredStates(states));
            } catch (Exception e) {
                Log.e(TAG, "Erreur restauration tags", e);
            } finally {
                restoringState = false;
            }
        });
    }

    private void applyRestoredStates(Map<String, Boolean> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        setCheckboxState(checkNude, states.getOrDefault("Nude", false));
        setCheckboxState(checkChildren, states.getOrDefault("Children", false));
        setCheckboxState(checkCreditCard, states.getOrDefault("Credit Card", false));
        setCheckboxState(checkIdentityDocument, states.getOrDefault("Identity Document", false));
        updateViewModelOnCheckboxChange();
    }

    private void setCheckboxState(CheckBox checkBox, boolean checked) {
        if (checkBox != null) {
            checkBox.setChecked(checked);
        }
    }
}
