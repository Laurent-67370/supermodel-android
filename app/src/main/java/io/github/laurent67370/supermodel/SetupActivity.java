package io.github.laurent67370.supermodel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * First screen: the folder Supermodel reads and writes (Supermodel/ of the
 * phone's storage, all files allowed; else the app's own folder), filled
 * with Config/ and Assets/ of the APK as the iOS app does (ios_main.mm of
 * supermodel-ios), then Supermodel itself (SupermodelActivity).
 */
public class SetupActivity extends Activity {
    static final String PREFS = "supermodel";
    static final String KEY_FOLDER = "folder";
    private static final String TAG = "Supermodel";
    private static final int ASK_STORAGE = 1;

    private boolean ownFolder;
    private boolean started;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ownFolder = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("own_folder", false);
        if (hasAccess() || ownFolder) {
            start();
        } else {
            showAsk();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the settings of Android with the access given
        if (!isFinishing() && hasAccess()) {
            start();
        }
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, ASK_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code == ASK_STORAGE && hasAccess()) {
            start();
        }
    }

    private File sharedFolder() {
        return new File(Environment.getExternalStorageDirectory(), "Supermodel");
    }

    private File appFolder() {
        File folder = getExternalFilesDir(null);
        return folder != null ? folder : getFilesDir();
    }

    private void showAsk() {
        final float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (24 * dp);
        column.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Supermodel");
        title.setTextColor(Color.rgb(0x3D, 0x8B, 0xFF));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
        text.setText(Lang.t("Supermodel reads your Model 3 games (.zip files, the same as on a computer) "
                + "in the « Supermodel » folder of your phone:\n\n"
                + "  Supermodel/ROMs     the games\n"
                + "  Supermodel/Config   the settings (Supermodel.ini)\n"
                + "  Supermodel/Supermodel.log   the log\n\n"
                + "For this, Android must allow Supermodel to access all files. "
                + "Without it, Supermodel uses its own folder:\n",
                "Supermodel lit vos jeux Model 3 (fichiers .zip, les mêmes que sur ordinateur) "
                + "dans le dossier « Supermodel » de votre téléphone :\n\n"
                + "  Supermodel/ROMs     les jeux\n"
                + "  Supermodel/Config   les réglages (Supermodel.ini)\n"
                + "  Supermodel/Supermodel.log   le journal\n\n"
                + "Pour cela, Android doit autoriser Supermodel à accéder à tous les fichiers. "
                + "Sans cette autorisation, Supermodel utilise son propre dossier :\n")
                + appFolder().getPath());
        column.addView(text);

        Button allow = new Button(this);
        allow.setText(Lang.t("Allow access to files", "Autoriser l'accès aux fichiers"));
        allow.setOnClickListener(v -> askAccess());
        column.addView(allow);

        Button without = new Button(this);
        without.setText(Lang.t("Continue without (the app's own folder)", "Continuer sans (dossier de l'application)"));
        without.setOnClickListener(v -> {
            ownFolder = true;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("own_folder", true).apply();
            start();
        });
        column.addView(without);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(column);
        setContentView(scroll);
    }

    private void start() {
        if (started) {
            return;
        }
        started = true;
        File folder = hasAccess() ? sharedFolder() : appFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            Log.e(TAG, "cannot make " + folder + ", the app's folder instead");
            folder = appFolder();
        }
        for (String name : new String[] { "Config", "ROMs", "NVRAM", "Saves", "Assets" }) {
            new File(folder, name).mkdirs();
        }
        // The list of the games and the pictures: always the app's; the
        // settings (Supermodel.ini), the user's: kept
        AssetManager assets = getAssets();
        copy(assets, "supermodel/Assets", new File(folder, "Assets"), true);
        copy(assets, "supermodel/Config", new File(folder, "Config"), true);

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, folder.getPath()).apply();
        Intent intent = new Intent(this, SupermodelActivity.class);
        intent.putExtra(KEY_FOLDER, folder.getPath());
        startActivity(intent);
        finish();
    }

    private void copy(AssetManager assets, String from, File to, boolean replace) {
        try {
            String[] names = assets.list(from);
            if (names != null && names.length > 0) {
                to.mkdirs();
                for (String name : names) {
                    copy(assets, from + "/" + name, new File(to, name), replace);
                }
                return;
            }
            if (to.exists() && (!replace || to.getName().equals("Supermodel.ini"))) {
                return;
            }
            try (InputStream in = assets.open(from); OutputStream out = new FileOutputStream(to)) {
                byte[] buffer = new byte[65536];
                for (int got; (got = in.read(buffer)) > 0;) {
                    out.write(buffer, 0, got);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "copy " + from, e);
        }
    }
}
