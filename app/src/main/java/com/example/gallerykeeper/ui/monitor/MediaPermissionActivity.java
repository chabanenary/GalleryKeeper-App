package com.example.gallerykeeper.ui.monitor;

import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Activity transparente qui présente l'UI système de consentement (IntentSender)
 * pour supprimer/modifier des médias à la place d'un Service.
 */
public class MediaPermissionActivity extends AppCompatActivity {
    public static final String EXTRA_URIS = "extra_uris";
    public static final String EXTRA_RECEIVER = "extra_receiver";
    private static final String TAG = "MediaPermissionAct";

    private ActivityResultLauncher<IntentSenderRequest> senderLauncher;
    private ResultReceiver receiver;
    private final Queue<Uri> queue = new LinkedList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Aucun layout: activité transparente

        senderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                this::onSenderResult
        );

        Intent intent = getIntent();
        receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
        ArrayList<Uri> list = intent.getParcelableArrayListExtra(EXTRA_URIS);
        if (list != null) queue.addAll(list);

        if (queue.isEmpty()) {
            sendResult(false);
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            launchDeleteForRPlus();
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Sur Q, on essaie de supprimer; si RecoverableSecurityException, on lance l'IntentSender
            launchDeleteForQ();
        } else {
            // Legacy: pas d'UI système
            deleteDirectLegacy();
        }
    }

    private void launchDeleteForRPlus() {
        try {
            IntentSender sender = MediaStore.createDeleteRequest(getContentResolver(), queue).getIntentSender();
            senderLauncher.launch(new IntentSenderRequest.Builder(sender).build());
        } catch (Exception e) {
            Log.e(TAG, "createDeleteRequest R+ a échoué", e);
            sendResult(false);
            finish();
        }
    }

    private void launchDeleteForQ() {
        // Traite la tête de queue; si RSE, demander consentement pour celle-ci
        Uri head = queue.peek();
        if (head == null) {
            sendResult(true);
            finish();
            return;
        }
        try {
            int rows = getContentResolver().delete(head, null, null);
            Log.d(TAG, "Q delete direct rows=" + rows + " uri=" + head);
            queue.poll();
            // Continuer tant que possible
            if (queue.isEmpty()) {
                sendResult(true);
                finish();
            } else {
                launchDeleteForQ();
            }
        } catch (RecoverableSecurityException rse) {
            try {
                IntentSender sender = rse.getUserAction().getActionIntent().getIntentSender();
                senderLauncher.launch(new IntentSenderRequest.Builder(sender).build());
            } catch (Exception ex) {
                Log.e(TAG, "Echec lancement RSE sender", ex);
                // On retire l'élément problématique et on continue
                queue.poll();
                if (queue.isEmpty()) {
                    sendResult(false);
                    finish();
                } else {
                    launchDeleteForQ();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur delete Q", e);
            queue.poll();
            if (queue.isEmpty()) {
                sendResult(false);
                finish();
            } else {
                launchDeleteForQ();
            }
        }
    }

    private void deleteDirectLegacy() {
        boolean ok = true;
        while (!queue.isEmpty()) {
            Uri u = queue.poll();
            try {
                int rows = getContentResolver().delete(u, null, null);
                ok = ok && (rows > 0);
            } catch (Exception e) {
                Log.e(TAG, "Erreur delete legacy", e);
                ok = false;
            }
        }
        sendResult(ok);
        finish();
    }

    private void onSenderResult(ActivityResult result) {
        boolean success = (result.getResultCode() == Activity.RESULT_OK);
        // Sur R+, le système supprimera les URIs passées
        // Sur Q, on reprend la suppression si plusieurs éléments
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && success) {
            // Après consentement pour l'élément tête, retenter la suppression et poursuivre la queue
            launchDeleteForQ();
            return;
        }
        sendResult(success);
        finish();
    }

    private void sendResult(boolean success) {
        if (receiver != null) {
            Bundle b = new Bundle();
            b.putBoolean("success", success);
            receiver.send(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED, b);
        }
    }
}

