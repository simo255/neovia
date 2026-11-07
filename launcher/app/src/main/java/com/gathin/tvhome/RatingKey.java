package com.gathin.tvhome;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Optional API key, encrypted with a device-local non-exportable key. */
final class RatingKey {
    private final AtomicFile file;
    private final String alias;
    private final boolean tmdb;
    RatingKey(Context context, boolean tmdb) {
        this.tmdb = tmdb;
        alias = tmdb ? "neovia-ratings-tmdb-v1" : "neovia-mdblist-v1";
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), tmdb ? "rating-tmdb-key.json" : "rating-key.json"));
    }
    boolean configured() { return file.getBaseFile().isFile(); }
    static boolean valid(String value, boolean tmdb) {
        return value != null && value.length() >= 16 && value.length() <= (tmdb ? 4096 : 128)
                && value.matches(tmdb ? "[A-Za-z0-9._~-]+" : "[A-Za-z0-9_-]+");
    }
    synchronized void save(String value) throws Exception {
        if (!valid(value, tmdb)) throw new IllegalArgumentException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("data", Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP))
                .toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception failure) { if (out != null) file.failWrite(out); throw failure; }
    }
    synchronized String load() throws Exception {
        if (!configured()) return "";
        if (file.getBaseFile().length() > 16384) throw new Exception();
        JSONObject data = new JSONObject(new String(Files.readAllBytes(file.getBaseFile().toPath()), StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(data.getString("iv"), Base64.NO_WRAP)));
        String value = new String(cipher.doFinal(Base64.decode(data.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8);
        if (!valid(value, tmdb)) throw new Exception();
        return value;
    }
    synchronized void clear() { file.delete(); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
