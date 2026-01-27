package com.example.gallerykeeper.ui.monitor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.content.Context;
import android.util.Log;

import com.example.gallerykeeper.Utils.Detector;

import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorPhotoViewModel extends ViewModel {

    // LiveData pour l'état du bouton "Scan". True si activé, false si désactivé.
    // Le bouton est désactivé (et devrait apparaître grisé dans la vue)
    // si aucune checkbox de catégorie de surveillance n'est sélectionnée
    private final MutableLiveData<Boolean> _isScanButtonEnabled = new MutableLiveData<>(false); // Initialement désactivé
    public LiveData<Boolean> isScanButtonEnabled() {
        return _isScanButtonEnabled;
    }

    // LiveData pour l'état du bouton "Monitor". True si activé, false si désactivé.
    // Le bouton est désactivé (et devrait apparaître grisé dans la vue)
    // si aucune checkbox de catégorie de surveillance n'est sélectionnée.
    private final MutableLiveData<Boolean> _isMonitorButtonEnabled = new MutableLiveData<>(false); // Initialement désactivé
    public LiveData<Boolean> isMonitorButtonEnabled() {
        return _isMonitorButtonEnabled;
    }

    // NEW: état de désactivation des checkboxes (true = désactivées)
    private final MutableLiveData<Boolean> _areCheckboxesDisabled = new MutableLiveData<>(false);
    public LiveData<Boolean> areCheckboxesDisabled() { return _areCheckboxesDisabled; }

    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isMonitoring = new MutableLiveData<>(false);

    private final AtomicBoolean isScanTaskRunning = new AtomicBoolean(false);
    private final AtomicBoolean isMonitorTaskRunning = new AtomicBoolean(false);

    private Context appContext;

    //For detection or recognition
    Detector Detector;

    public interface MonitoringCallback {
        void onMonitoringTask(Context context);
        void onScanTask(Context context);
    }
    private MonitoringCallback monitoringCallback;

    public void setMonitoringCallback(MonitoringCallback callback) {
        this.monitoringCallback = callback;
    }

    // Constructeur public vide (ou pas de constructeur du tout, Java en fournira un par défaut)
    public MonitorPhotoViewModel(Context context) {
        this.appContext = context;
    }

    // Événement: pour activer/désactiver les boutons en fonction des checkboxes
    private volatile boolean lastAnyCheckboxSelected = false; // Nouvel état mémorisé des checkboxes
    public void onCheckboxSelectionChanged(boolean anyCheckboxSelected) {
        lastAnyCheckboxSelected = anyCheckboxSelected;
        Boolean monitoring = isMonitoring.getValue();
        Boolean scanning = isScanning.getValue();
        if (monitoring != null && monitoring) {
            // Monitoring en cours: bouton Monitor actif (pour arrêter), Scan désactivé
            _isMonitorButtonEnabled.setValue(true);
            _isScanButtonEnabled.setValue(false);
            // Checkboxes doivent rester désactivées pendant un monitoring actif
            _areCheckboxesDisabled.setValue(true);
        } else if (scanning != null && scanning) {
            // Scan en cours: bouton Scan actif (pour arrêter), Monitor désactivé
            _isScanButtonEnabled.setValue(true);
            _isMonitorButtonEnabled.setValue(false);
            _areCheckboxesDisabled.setValue(true);
        } else {
            // Aucun processus actif: activer/désactiver selon sélection
            _isScanButtonEnabled.setValue(anyCheckboxSelected);
            _isMonitorButtonEnabled.setValue(anyCheckboxSelected);
            _areCheckboxesDisabled.setValue(false);
        }
    }

    // Méthodes pour obtenir les LiveData des états de scan et de monitoring
    public LiveData<Boolean> getIsScanning() {
        return isScanning;
    }

    public LiveData<Boolean> getIsMonitoring() {
        return isMonitoring;
    }

    public void setContext(Context context) {
        this.appContext = context.getApplicationContext(); // Utilisez le contexte d'application pour éviter les fuites
    }


    // --- Scan ---
    public void startScanning() {
        // Vérifiez si une tâche est déjà en cours pour éviter d'en lancer plusieurs
        if (isScanTaskRunning.get()) {
            Log.d("MonitorPhotoViewModel", "Une tâche de Scan est déjà en cours.");
            return;
        }
        isScanning.setValue(true);
        isMonitoring.setValue(false);
        _isScanButtonEnabled.setValue(true); // actif pour autoriser arrêt
        _isMonitorButtonEnabled.setValue(false);
        _areCheckboxesDisabled.setValue(true); // Désactiver les checkboxes pendant l'exécution
        startScanTaskInternal();
    }


    public void stopScanning() {
        Log.d("MonitorPhotoViewModel", "stopScanning() appelé.");
        // Si la tâche est en cours, on lui demande de s'arrêter.
        // Le flag isMonitorTaskRunning sera mis à false dans le bloc finally de la tâche
        // ou ici si on l'arrête "de l'extérieur".
        if (isScanTaskRunning.getAndSet(false)) { // Atomiquement mettre à false et obtenir l'ancienne valeur
            Log.d("MonitorPhotoViewModel", "Signal d'arrêt envoyé à la tâche de Scan.");
        }
        isScanning.setValue(false);
        // Restaurer selon lastAnyCheckboxSelected
        _isScanButtonEnabled.setValue(lastAnyCheckboxSelected);
        _isMonitorButtonEnabled.setValue(lastAnyCheckboxSelected);
        _areCheckboxesDisabled.setValue(false); // Réactiver après arrêt
    }

    private void stopScanningInternal() {
        // isMonitorTaskRunning.set(false) est thread-safe
        // Si la tâche était en cours, on la marque comme terminée.
        // Le getAndSet dans stopMonitoring() gère le cas où l'arrêt est initié de l'extérieur.
        // Ici, la tâche se termine d'elle-même.
        isScanTaskRunning.set(false);

        // Mettre à jour le LiveData isScanning en utilisant postValue
        // car cette méthode peut être appelée depuis le thread de Scan.
        isScanning.postValue(false);
        _isScanButtonEnabled.postValue(lastAnyCheckboxSelected);
        _isMonitorButtonEnabled.postValue(lastAnyCheckboxSelected);
        _areCheckboxesDisabled.postValue(false);
        Log.d("MonitorPhotoViewModel", "stopScanningInternal: isScanning postValue(false)");
    }


    // --- Monitoring ---
    public void startMonitoring() {
        // Vérifiez si une tâche est déjà en cours pour éviter d'en lancer plusieurs
        if (isMonitorTaskRunning.get()) {
            Log.d("MonitorPhotoViewModel", "Une tâche de monitoring est déjà en cours.");
            return;
        }

        isMonitoring.setValue(true); // Indique que le processus de monitoring est actif
        isScanning.setValue(false); // Assurez-vous que le scan est arrêté
        _isMonitorButtonEnabled.setValue(true); // actif pour permettre STOP
        _isScanButtonEnabled.setValue(false);
        _areCheckboxesDisabled.setValue(true);
        startMonitoringTaskInternal();
    }



    public void stopMonitoring() {
        Log.d("MonitorPhotoViewModel", "stopMonitoring() appelé.");
        // Si la tâche est en cours, on lui demande de s'arrêter.
        // Le flag isMonitorTaskRunning sera mis à false dans le bloc finally de la tâche
        // ou ici si on l'arrête "de l'extérieur".
        if (isMonitorTaskRunning.getAndSet(false)) { // Atomiquement mettre à false et obtenir l'ancienne valeur
            Log.d("MonitorPhotoViewModel", "Signal d'arrêt envoyé à la tâche de monitoring.");
        }
        isMonitoring.setValue(false);
        _isMonitorButtonEnabled.setValue(lastAnyCheckboxSelected);
        _isScanButtonEnabled.setValue(lastAnyCheckboxSelected);
        _areCheckboxesDisabled.setValue(false);
    }



    private void stopMonitoringInternal() {
        // isMonitorTaskRunning.set(false) est thread-safe
        // Si la tâche était en cours, on la marque comme terminée.
        // Le getAndSet dans stopMonitoring() gère le cas où l'arrêt est initié de l'extérieur.
        // Ici, la tâche se termine d'elle-même.
        isMonitorTaskRunning.set(false);

        // Mettre à jour le LiveData _isMonitoring en utilisant postValue
        // car cette méthode peut être appelée depuis le thread de monitoring.
        isMonitoring.postValue(false);
        _isMonitorButtonEnabled.postValue(lastAnyCheckboxSelected);
        _isScanButtonEnabled.postValue(lastAnyCheckboxSelected);
        _areCheckboxesDisabled.postValue(false);
        Log.d("MonitorPhotoViewModel", "stopMonitoringInternal: isMonitoring postValue(false)");
    }



    private void startScanTaskInternal() {
        new Thread(() -> {
            // Mettre à true seulement quand le thread démarre réellement son travail
            if (!isScanTaskRunning.compareAndSet(false, true)) {
                Log.d("Scan Task", "La tâche n'a pas pu démarrer (déjà en cours ou autre condition).");
                return;
            }

            Log.d("Scan Task", "Tâche de Scan démarrée.");
            try {

                if (!isScanTaskRunning.get()) { // Vérifier si on doit s'arrêter prématurément
                    Log.d("Scan Task", "Scan interrompu ");
                    return;
                }
                // Appel du callback onScanTask
                if (monitoringCallback != null && appContext != null) {
                    monitoringCallback.onScanTask(appContext);
                }

            } finally {
                // Ce bloc finally s'exécutera toujours, que la boucle se termine normalement
                // ou à cause d'une interruption ou d'un break.
                Log.d("Sacanning Task", "Tâche de scan terminée ou interrompue.");
                // Important: Appeler stopMonitoring() depuis le thread principal si elle modifie des LiveData
                // qui sont observés par l'UI.
                // Cependant, stopMonitoring() dans votre cas modifie _isMonitoring et isMonitorTaskRunning,
                // ce qui est sûr à faire depuis ce thread car les LiveData gèrent la synchronisation.
                stopScanningInternal(); // Appel direct ici
            }
        }).start();
    }


    private void startMonitoringTaskInternal() {

            Log.d("Monitoring Internal", " monitoring interne démarrée.");

                if (isScanTaskRunning.get()) { // Vérifier si on doit s'arrêter prématurément
                    Log.d("Monitoring Internal", "Scan en cours, pas de monitoring possible ");
                     stopMonitoringInternal();
                    return;
                }

                // On marque le drapeau interne pour cohérence si le service ne le fait pas déjà.
                isMonitorTaskRunning.set(true);
                if (monitoringCallback != null && appContext != null) {
                    monitoringCallback.onMonitoringTask(appContext); // Appel de la méthode du fragment
                }

    }

    public void applyMonitoringStateFromService(boolean active) {
        // Répercute l’état du service dans l’UI sans déclencher de tâche
        isMonitoring.postValue(active);
        if (active) {
            // Monitoring actif: Scan désactivé; Monitor activé pour permettre l'arrêt
            _isMonitorButtonEnabled.postValue(true);
            _isScanButtonEnabled.postValue(false);
            _areCheckboxesDisabled.postValue(true);
            isMonitorTaskRunning.set(true);
        } else {
            // Monitoring inactif: respecter l'état des checkboxes
            _isMonitorButtonEnabled.postValue(lastAnyCheckboxSelected);
            _isScanButtonEnabled.postValue(lastAnyCheckboxSelected);
            _areCheckboxesDisabled.postValue(false);
            isMonitorTaskRunning.set(false);
        }
    }

    public void enableScanButton(boolean active) {
        _isScanButtonEnabled.postValue(active);
    }
}

