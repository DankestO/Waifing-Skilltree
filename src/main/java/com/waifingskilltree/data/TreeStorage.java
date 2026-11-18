package com.waifingskilltree.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Trees loaded from datapacks ({@code data/<ns>/waifingskilltree/trees/*.json}, see {@link TreeReloadListener}).
 * It is the only source: edit them with the HTML editor and apply with /reload.
 */
public final class TreeStorage {
    private static final Map<String, SkillTree> TREES = new LinkedHashMap<>();

    private TreeStorage() {}

    public static Map<String, SkillTree> all() {
        return TREES;
    }

    public static SkillTree get(String id) {
        return TREES.get(id);
    }

    public static String sanitizeId(String id) {
        String s = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return s.isEmpty() ? "tree" : s;
    }

    public static void setDatapackTrees(Map<String, SkillTree> trees) {
        TREES.clear();
        TREES.putAll(trees);
    }

    // ---------- network (de)serialization ----------

    public static byte[] encodeAll() {
        JsonObject root = new JsonObject();
        TREES.forEach((id, t) -> root.add(id, t.toJson()));
        return gzip(root.toString());
    }

    public static Map<String, SkillTree> decodeAll(byte[] data) {
        Map<String, SkillTree> out = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(gunzip(data)).getAsJsonObject();
        root.entrySet().forEach(e -> out.put(e.getKey(), SkillTree.fromJson(e.getKey(), e.getValue().getAsJsonObject())));
        return out;
    }

    private static byte[] gzip(String s) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); GZIPOutputStream g = new GZIPOutputStream(bos)) {
            g.write(s.getBytes(StandardCharsets.UTF_8));
            g.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String gunzip(byte[] b) {
        try (GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(b))) {
            return new String(g.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
