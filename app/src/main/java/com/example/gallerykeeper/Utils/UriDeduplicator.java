package com.example.gallerykeeper.Utils;

import java.util.Collection;
import java.util.Set;

/**
 * Logique JVM pure pour dédoublonner les "tâches" par URI.
 * Objectif : reproduire la logique du service (urisBeingProcessed + lastProcessed queues)
 * sans dépendre de android.net.Uri.
 */
public final class UriDeduplicator {
    private UriDeduplicator() {
    }

    /**
     * @return true si l'URI doit être ignorée (déjà en cours ou récemment traitée)
     */
    public static boolean shouldIgnore(
            String uri,
            Set<String> beingProcessed,
            Collection<String> lastProcessed,
            Collection<String> lastTargetProcessed
    ) {
        if (uri == null || uri.isEmpty()) {
            return true;
        }
        if (beingProcessed != null && beingProcessed.contains(uri)) {
            return true;
        }
        if (lastProcessed != null && lastProcessed.contains(uri)) {
            return true;
        }
        return lastTargetProcessed != null && lastTargetProcessed.contains(uri);
    }
}
