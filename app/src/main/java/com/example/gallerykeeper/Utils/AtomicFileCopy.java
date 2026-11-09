package com.example.gallerykeeper.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copie JVM pure (testable) : écrit d'abord dans un fichier temporaire, force l'écriture sur le
 * disque, puis le renomme en fichier final. Le fichier final est donc soit absent (ou l'ancien,
 * intact), soit complet : une copie interrompue (app tuée, disque plein) ne laisse jamais de
 * fichier final tronqué.
 */
public final class AtomicFileCopy {

    private AtomicFileCopy() {
    }

    /**
     * @param in      flux source (fermé par l'appelant)
     * @param outFile fichier final, remplacé seulement si la copie est complète
     * @throws IOException si la lecture, l'écriture ou le renommage échoue
     */
    public static void copy(InputStream in, File outFile) throws IOException {
        File tmpFile = new File(outFile.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                // Données réellement sur le disque avant le renommage
                out.getFD().sync();
            }
            // Renommage dans le même dossier : remplace le fichier final d'un seul coup
            if (!tmpFile.renameTo(outFile)) {
                throw new IOException("Renommage impossible : " + tmpFile + " -> " + outFile);
            }
        } finally {
            // Copie ratée : pas de fichier temporaire laissé derrière
            if (tmpFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
        }
    }
}
