package com.ragdocs.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MockEmbeddingProvider implements EmbeddingProvider {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is",
            "it", "of", "on", "or", "that", "the", "this", "to", "with", "如何", "什么"
    );

    private final int dimensions;
    private final String modelName;

    public MockEmbeddingProvider(int dimensions, String modelName) {
        this.dimensions = dimensions;
        this.modelName = modelName;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingCallException("Embedding 输入不能为空");
        }
        return texts.stream().map(this::embedOne).toList();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private float[] embedOne(String text) {
        float[] vector = new float[dimensions];
        List<String> tokens = tokenize(text == null ? "" : text);
        if (tokens.isEmpty()) {
            tokens = List.of(text == null ? "" : text);
        }
        for (String token : tokens) {
            int hash = positiveHash(token);
            int index = hash % dimensions;
            float weight = tokenWeight(token);
            vector[index] += weight;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint) && codePoint < 128) {
                flushCjk(tokens, cjk);
                ascii.appendCodePoint(Character.toLowerCase(codePoint));
            } else if (isCjk(codePoint)) {
                flushAscii(tokens, ascii);
                cjk.appendCodePoint(codePoint);
            } else {
                flushAscii(tokens, ascii);
                flushCjk(tokens, cjk);
            }
            offset += Character.charCount(codePoint);
        }
        flushAscii(tokens, ascii);
        flushCjk(tokens, cjk);
        return tokens;
    }

    private void flushAscii(List<String> tokens, StringBuilder ascii) {
        if (ascii.isEmpty()) {
            return;
        }
        String word = ascii.toString().toLowerCase(Locale.ROOT);
        ascii.setLength(0);
        if (word.length() < 2 || STOP_WORDS.contains(word)) {
            return;
        }
        tokens.add(word);
        if (word.length() > 5) {
            for (int i = 0; i <= word.length() - 4; i++) {
                tokens.add(word.substring(i, i + 4));
            }
        }
    }

    private void flushCjk(List<String> tokens, StringBuilder cjk) {
        if (cjk.isEmpty()) {
            return;
        }
        String value = cjk.toString();
        cjk.setLength(0);
        int[] points = value.codePoints().toArray();
        if (points.length == 1) {
            tokens.add(value);
            return;
        }
        Set<String> unique = new HashSet<>();
        for (int n = 2; n <= Math.min(3, points.length); n++) {
            for (int i = 0; i <= points.length - n; i++) {
                String token = new String(points, i, n);
                if (unique.add(token)) {
                    tokens.add(token);
                }
            }
        }
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private int positiveHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return ((hash[0] & 0x7F) << 24)
                    | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8)
                    | (hash[3] & 0xFF);
        } catch (NoSuchAlgorithmException ex) {
            return Math.abs(token.hashCode());
        }
    }

    private float tokenWeight(String token) {
        return token.codePoints().count() >= 4 ? 2.0F : 1.0F;
    }

    private void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1.0F;
            return;
        }
        double sqrt = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / sqrt);
        }
    }
}
