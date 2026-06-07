package com.esempio.galleriaduplicati;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 101;
    private static final int HAMMING_THRESHOLD = 2;

    private Button btnScan;
    private Button btnDelete;
    private TextView tvStatus;
    private TextView tvResultCount;
    private TextView tvHint;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private View layoutProgress;
    private ListView listViewDuplicates;

    private DuplicateManager duplicateManager;
    private ExecutorService executor;
    private Handler mainHandler;

    private List<DuplicateEntry> entries = new ArrayList<>();
    private DuplicateAdapter adapter;

    static class DuplicateEntry {
        File file;
        String displayName;
        boolean isHeader;
        boolean isSelected;

        DuplicateEntry(File file, String displayName, boolean isHeader, boolean isSelected) {
            this.file = file;
            this.displayName = displayName;
            this.isHeader = isHeader;
            this.isSelected = isSelected;
        }
    }

    class DuplicateAdapter extends ArrayAdapter<DuplicateEntry> {

        DuplicateAdapter() {
            super(MainActivity.this, android.R.layout.simple_list_item_multiple_choice, entries);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            DuplicateEntry entry = entries.get(position);

            if (entry.isHeader) {
                if (convertView == null || convertView.getTag() == null
                        || !convertView.getTag().equals("header")) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(android.R.layout.simple_list_item_1, parent, false);
                    convertView.setTag("header");
                }
                TextView tv = convertView.findViewById(android.R.id.text1);
                tv.setText(entry.displayName);
                tv.setTextColor(0xFFFFB300);
                tv.setTextSize(12f);
                tv.setPadding(24, 16, 24, 4);
                convertView.setBackgroundColor(0xFF1A1A1A);
                convertView.setClickable(false);
                convertView.setEnabled(false);
            } else {
                if (convertView == null || convertView.getTag() == null
                        || !convertView.getTag().equals("item")) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
                    convertView.setTag("item");
                }
                CheckedTextView ctv = convertView.findViewById(android.R.id.text1);
                ctv.setText(entry.displayName);
                ctv.setChecked(entry.isSelected);

                if (entry.isSelected) {
                    ctv.setTextColor(0xFFEF9A9A);
                    convertView.setBackgroundColor(0xFF1C0000);
                } else {
                    ctv.setTextColor(0xFFA5D6A7);
                    convertView.setBackgroundColor(0xFF001A00);
                }

                ctv.setTextSize(13f);
                ctv.setPadding(32, 12, 24, 12);
            }
            return convertView;
        }

        @Override
        public boolean isEnabled(int position) {
            return !entries.get(position).isHeader;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan        = findViewById(R.id.btnScan);
        btnDelete      = findViewById(R.id.btnDelete);
        tvStatus       = findViewById(R.id.tvStatus);
        tvResultCount  = findViewById(R.id.tvResultCount);
        tvHint         = findViewById(R.id.tvHint);
        progressBar    = findViewById(R.id.progressBar);
        tvProgress     = findViewById(R.id.tvProgress);
        layoutProgress = findViewById(R.id.layoutProgress);
        listViewDuplicates = findViewById(R.id.listViewDuplicates);

        duplicateManager = new DuplicateManager();
        executor    = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        adapter = new DuplicateAdapter();
        listViewDuplicates.setAdapter(adapter);
        listViewDuplicates.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        listViewDuplicates.setOnItemClickListener((parent, view, position, id) -> {
            DuplicateEntry entry = entries.get(position);
            if (entry.isHeader) return;
            entry.isSelected = !entry.isSelected;
            listViewDuplicates.setItemChecked(position, entry.isSelected);
            adapter.notifyDataSetChanged();
            updateDeleteButton();
        });

        btnScan.setOnClickListener(v -> checkPermissionsAndScan());
        btnDelete.setOnClickListener(v -> confirmAndDelete());
    }

    private void checkPermissionsAndScan() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
        }

        if (needed.isEmpty()) {
            startScan();
        } else {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "Permessi necessari per accedere alle foto.", Toast.LENGTH_LONG).show();
                tvStatus.setText("Permessi negati. Impossibile procedere.");
            }
        }
    }

    private void startScan() {
        entries.clear();
        adapter.notifyDataSetChanged();
        btnDelete.setVisibility(View.GONE);
        tvResultCount.setVisibility(View.GONE);
        tvHint.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        tvProgress.setText("0%");
        btnScan.setEnabled(false);
        tvStatus.setText("Ricerca immagini in DCIM/Camera...");

        executor.execute(() -> {
            List<File> imageFiles = getImagesFromDCIM();

            if (imageFiles.isEmpty()) {
                mainHandler.post(() -> {
                    tvStatus.setText("Nessuna immagine trovata in DCIM/Camera.");
                    layoutProgress.setVisibility(View.GONE);
                    btnScan.setEnabled(true);
                });
                return;
            }

            int total = imageFiles.size();
            mainHandler.post(() -> tvStatus.setText("Calcolo pHash per " + total + " immagini..."));

            long[] hashes = new long[total];
            for (int i = 0; i < total; i++) {
                hashes[i] = duplicateManager.computePHash(imageFiles.get(i));
                final int progress = (int) ((i + 1) * 100.0 / total);
                final int current  = i + 1;
                mainHandler.post(() -> {
                    progressBar.setProgress(progress);
                    tvProgress.setText(progress + "%");
                    tvStatus.setText("Analisi: " + current + "/" + total);
                });
            }

            mainHandler.post(() -> tvStatus.setText("Ricerca duplicati..."));

            List<List<Integer>> clusters =
                    duplicateManager.clusterDuplicates(imageFiles, hashes, HAMMING_THRESHOLD);

            final List<DuplicateEntry> newEntries = new ArrayList<>();
            int totalDuplicateFiles = 0;

            for (int g = 0; g < clusters.size(); g++) {
                List<Integer> group = clusters.get(g);
                int groupNumber = g + 1;

                newEntries.add(new DuplicateEntry(
                        null,
                        "── GRUPPO " + groupNumber + " ──  " + group.size() + " file simili",
                        true,
                        false
                ));

                for (int k = 0; k < group.size(); k++) {
                    int idx       = group.get(k);
                    File file     = imageFiles.get(idx);
                    boolean isDup = (k > 0);

                    String label;
                    if (!isDup) {
                        label = "✓ ORIGINALE  " + file.getName() + "  [" + formatSize(file.length()) + "]";
                    } else {
                        label = "✗ DUPLICATO  " + file.getName() + "  [" + formatSize(file.length()) + "]";
                        totalDuplicateFiles++;
                    }
                    newEntries.add(new DuplicateEntry(file, label, false, isDup));
                }
            }

            final int finalTotalDup = totalDuplicateFiles;
            final int finalClusters = clusters.size();

            mainHandler.post(() -> {
                layoutProgress.setVisibility(View.GONE);
                btnScan.setEnabled(true);

                if (finalClusters == 0) {
                    tvStatus.setText("✅ Nessun duplicato trovato tra " + total + " immagini.");
                    tvResultCount.setVisibility(View.GONE);
                    tvHint.setVisibility(View.GONE);
                } else {
                    tvStatus.setText("Analisi completata: " + total + " immagini esaminate.");
                    tvResultCount.setText("⚠ Trovati " + finalClusters + " gruppi, " + finalTotalDup + " file eliminabili");
                    tvResultCount.setVisibility(View.VISIBLE);
                    tvHint.setVisibility(View.VISIBLE);

                    entries.clear();
                    entries.addAll(newEntries);
                    adapter.notifyDataSetChanged();

                    for (int i = 0; i < entries.size(); i++) {
                        listViewDuplicates.setItemChecked(i, entries.get(i).isSelected);
                    }

                    updateDeleteButton();
                }
            });
        });
    }

    private List<File> getImagesFromDCIM() {
        List<File> result = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID };
        String selection = MediaStore.Images.Media.DATA + " LIKE ?";
        String[] selectionArgs = { "%DCIM/Camera/%" };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " ASC";

        try (Cursor cursor = cr.query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int colData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                do {
                    String path = cursor.getString(colData);
                    if (path != null) {
                        File f = new File(path);
                        if (f.exists() && f.isFile() && f.length() > 0) {
                            result.add(f);
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            result.addAll(scanDirectoryFallback());
        }

        if (result.isEmpty()) {
            result.addAll(scanDirectoryFallback());
        }

        return result;
    }

    private List<File> scanDirectoryFallback() {
        List<File> result = new ArrayList<>();
        File dcimDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        if (!dcimDir.exists() || !dcimDir.isDirectory()) return result;

        File[] files = dcimDir.listFiles();
        if (files == null) return result;

        for (File f : files) {
            if (f.isFile() && isImageFile(f.getName())) {
                result.add(f);
            }
        }
        return result;
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void updateDeleteButton() {
        long selectedCount = entries.stream().filter(e -> !e.isHeader && e.isSelected).count();
        if (selectedCount > 0) {
            btnDelete.setText("🗑  ELIMINA " + selectedCount + " FILE SELEZIONATI");
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            btnDelete.setVisibility(View.GONE);
        }
    }

    private void confirmAndDelete() {
        List<DuplicateEntry> toDelete = new ArrayList<>();
        for (DuplicateEntry entry : entries) {
            if (!entry.isHeader && entry.isSelected) {
                toDelete.add(entry);
            }
        }

        if (toDelete.isEmpty()) {
            Toast.makeText(this, "Nessun file selezionato.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Stai per eliminare DEFINITIVAMENTE ").append(toDelete.size()).append(" file:\n\n");
        int shown = Math.min(toDelete.size(), 5);
        for (int i = 0; i < shown; i++) {
            sb.append("• ").append(toDelete.get(i).file.getName()).append("\n");
        }
        if (toDelete.size() > 5) {
            sb.append("• ... e altri ").append(toDelete.size() - 5).append(" file\n");
        }
        sb.append("\n⚠ Questa operazione è IRREVERSIBILE.");

        new AlertDialog.Builder(this)
                .setTitle("Conferma Eliminazione")
                .setMessage(sb.toString())
                .setPositiveButton("ELIMINA", (dialog, which) -> performDelete(toDelete))
                .setNegativeButton("ANNULLA", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void performDelete(List<DuplicateEntry> toDelete) {
        executor.execute(() -> {
            int deleted = 0;
            int failed  = 0;
            List<DuplicateEntry> successList = new ArrayList<>();

            for (DuplicateEntry entry : toDelete) {
                boolean success = deleteFilePhysically(entry.file);
                if (success) {
                    deleted++;
                    successList.add(entry);
                } else {
                    failed++;
                }
            }

            final int finalDeleted = deleted;
            final int finalFailed  = failed;

            mainHandler.post(() -> {
                entries.removeAll(successList);
                cleanOrphanedHeaders();
                adapter.notifyDataSetChanged();
                updateDeleteButton();

                String msg = "✅ Eliminati: " + finalDeleted + " file";
                if (finalFailed > 0) msg += "  ❌ Falliti: " + finalFailed;
                tvStatus.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        });
    }

    private boolean deleteFilePhysically(File file) {
        if (file == null || !file.exists()) return true;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean deleted = file.delete();
            if (deleted) notifyMediaStore(file);
            return deleted;
        }

        try {
            ContentResolver cr = getContentResolver();
            Uri collection    = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String selection  = MediaStore.Images.Media.DATA + " = ?";
            String[] selArgs  = { file.getAbsolutePath() };

            int rowsDeleted = cr.delete(collection, selection, selArgs);
            if (rowsDeleted > 0) return true;

            String[] proj = { MediaStore.Images.Media._ID };
            try (Cursor cursor = cr.query(collection, proj, selection, selArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    Uri fileUri = ContentUris.withAppendedId(collection, id);
                    int n = cr.delete(fileUri, null, null);
                    return n > 0;
                }
            }

            return file.delete();

        } catch (Exception e) {
            return file.delete();
        }
    }

    private void notifyMediaStore(File file) {
        try {
            ContentResolver cr = getContentResolver();
            String selection  = MediaStore.Images.Media.DATA + " = ?";
            String[] selArgs  = { file.getAbsolutePath() };
            cr.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selArgs);
        } catch (Exception ignored) {}
    }

    private void cleanOrphanedHeaders() {
        List<DuplicateEntry> toRemove = new ArrayList<>();
        int i = 0;
        while (i < entries.size()) {
            if (entries.get(i).isHeader) {
                int fileCount = 0;
                int j = i + 1;
                while (j < entries.size() && !entries.get(j).isHeader) {
                    fileCount++;
                    j++;
                }
                if (fileCount <= 1) {
                    toRemove.add(entries.get(i));
                    if (fileCount == 1) toRemove.add(entries.get(i + 1));
                }
                i = j;
            } else {
                i++;
            }
        }
        entries.removeAll(toRemove);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
