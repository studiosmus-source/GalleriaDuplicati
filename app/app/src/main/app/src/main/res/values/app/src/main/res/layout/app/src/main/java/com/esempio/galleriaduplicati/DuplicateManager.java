package com.esempio.galleriaduplicati;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DuplicateManager {

    private static final int HASH_SIZE = 8;

    public long computePHash(File imageFile) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap original = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            if (original == null) return -1L;

            Bitmap small = Bitmap.createScaledBitmap(original, HASH_SIZE, HASH_SIZE, true);
            original.recycle();

            int totalPixels = HASH_SIZE * HASH_SIZE;
            double[] grayscale = new double[totalPixels];
            double sum = 0.0;

            for (int y = 0; y < HASH_SIZE; y++) {
                for (int x = 0; x < HASH_SIZE; x++) {
                    int pixel = small.getPixel(x, y);
                    double luma = 0.299 * Color.red(pixel)
                                + 0.587 * Color.green(pixel)
                                + 0.114 * Color.blue(pixel);
                    grayscale[y * HASH_SIZE + x] = luma;
                    sum += luma;
                }
            }
            small.recycle();

            double mean = sum / totalPixels;

            long hash = 0L;
            for (int i = 0; i < totalPixels; i++) {
                if (grayscale[i] >= mean) {
                    hash |= (1L << i);
                }
            }

            return hash;

        } catch (Exception e) {
            return -1L;
        }
    }

    public int hammingDistance(long hash1, long hash2) {
        long xor = hash1 ^ hash2;
        return Long.bitCount(xor);
    }

    public List<List<Integer>> clusterDuplicates(List<File> files, long[] hashes, int maxDistance) {
        int n = files.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            if (hashes[i] == -1L) continue;
            for (int j = i + 1; j < n; j++) {
                if (hashes[j] == -1L) continue;
                int dist = hammingDistance(hashes[i], hashes[j]);
                if (dist <= maxDistance) {
                    int rootI = findRoot(parent, i);
                    int rootJ = findRoot(parent, j);
                    if (rootI != rootJ) {
                        parent[rootJ] = rootI;
                    }
                }
            }
        }

        java.util.Map<Integer, List<Integer>> clusterMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (hashes[i] == -1L) continue;
            int root = findRoot(parent, i);
            if (!clusterMap.containsKey(root)) {
                clusterMap.put(root, new ArrayList<>());
            }
            clusterMap.get(root).add(i);
        }

        List<List<Integer>> result = new ArrayList<>();
        for (List<Integer> cluster : clusterMap.values()) {
            if (cluster.size() >= 2) {
                result.add(cluster);
            }
        }
        return result;
    }

    private int findRoot(int[] parent, int i) {
        if (parent[i] != i) {
            parent[i] = findRoot(parent, parent[i]);
        }
        return parent[i];
    }
}
