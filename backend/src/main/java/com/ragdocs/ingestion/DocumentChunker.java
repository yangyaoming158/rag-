package com.ragdocs.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunker {
    static final int MIN_CHARS = 200;
    static final int PLAIN_TARGET_MIN = 600;
    static final int TARGET_MAX = 900;
    static final int MAX_CHARS = 1000;
    static final int OVERLAP_CHARS = 120;

    public List<ChunkDraft> chunk(ParsedDocument document) {
        if (document.fullText().replaceAll("\\s+", "").length() < MIN_CHARS) {
            throw new IngestionException("解析文本少于 200 字，无法切块");
        }
        List<SeedChunk> seeds = document.kind() == DocumentKind.MARKDOWN
                ? markdownSeeds(document.blocks())
                : plainSeeds(document.blocks());
        List<SeedChunk> bounded = new ArrayList<>();
        for (SeedChunk seed : seeds) {
            if (seed.charLen() > TARGET_MAX) {
                bounded.addAll(splitLongSeed(seed));
            } else {
                bounded.add(seed);
            }
        }
        List<SeedChunk> normalized = rebalanceSmallChunks(bounded);
        List<ChunkDraft> drafts = new ArrayList<>(normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            SeedChunk seed = normalized.get(i);
            if (seed.charLen() < MIN_CHARS || seed.charLen() > MAX_CHARS) {
                throw new IngestionException("切块长度不在 200-1000 字范围内: chunkIndex="
                        + i + ", charLen=" + seed.charLen());
            }
            drafts.add(new ChunkDraft(
                    seed.content(),
                    i,
                    seed.headingPath(),
                    seed.pageStart(),
                    seed.pageEnd(),
                    seed.charLen()
            ));
        }
        return drafts;
    }

    private List<SeedChunk> markdownSeeds(List<ParsedBlock> blocks) {
        List<SeedChunk> merged = new ArrayList<>();
        SeedChunk pending = null;
        for (ParsedBlock block : blocks) {
            SeedChunk seed = new SeedChunk(block.text(), block.headingPath(), block.pageStart(), block.pageEnd());
            if (seed.content().isBlank()) {
                continue;
            }
            if (pending == null) {
                pending = seed;
                continue;
            }
            if (pending.charLen() < MIN_CHARS || seed.charLen() < MIN_CHARS) {
                SeedChunk combined = pending.merge(seed);
                if (combined.charLen() <= MAX_CHARS) {
                    pending = combined;
                    continue;
                }
            }
            merged.add(pending);
            pending = seed;
        }
        if (pending != null) {
            if (!merged.isEmpty() && pending.charLen() < MIN_CHARS) {
                SeedChunk previous = merged.get(merged.size() - 1);
                SeedChunk combined = previous.merge(pending);
                if (combined.charLen() <= MAX_CHARS) {
                    merged.set(merged.size() - 1, combined);
                } else {
                    merged.add(pending);
                }
            } else {
                merged.add(pending);
            }
        }
        return merged;
    }

    private List<SeedChunk> plainSeeds(List<ParsedBlock> blocks) {
        List<Paragraph> paragraphs = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            for (String paragraph : paragraphsOf(block.text())) {
                paragraphs.add(new Paragraph(paragraph, block.pageStart(), block.pageEnd()));
            }
        }

        List<SeedChunk> chunks = new ArrayList<>();
        SeedChunk current = null;
        for (Paragraph paragraph : paragraphs) {
            SeedChunk seed = new SeedChunk(paragraph.text(), null, paragraph.pageStart(), paragraph.pageEnd());
            if (seed.charLen() > TARGET_MAX) {
                if (current != null) {
                    chunks.add(current);
                    current = null;
                }
                chunks.addAll(splitLongSeed(seed));
                continue;
            }
            if (current == null) {
                current = seed;
                continue;
            }
            SeedChunk combined = current.merge(seed);
            if (current.charLen() >= PLAIN_TARGET_MIN && combined.charLen() > TARGET_MAX) {
                chunks.add(current);
                current = seed.withPrefix(tail(current.content()));
            } else if (combined.charLen() <= TARGET_MAX) {
                current = combined;
            } else {
                chunks.add(current);
                current = seed;
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return chunks;
    }

    private List<SeedChunk> splitLongSeed(SeedChunk seed) {
        List<SeedChunk> chunks = new ArrayList<>();
        SeedChunk current = null;
        for (String text : paragraphsOf(seed.content())) {
            if (text.length() > TARGET_MAX) {
                if (current != null) {
                    chunks.add(current);
                    current = null;
                }
                chunks.addAll(splitLongParagraph(text, seed));
                continue;
            }
            SeedChunk paragraphSeed = new SeedChunk(text, seed.headingPath(), seed.pageStart(), seed.pageEnd());
            if (current == null) {
                current = paragraphSeed;
                continue;
            }
            SeedChunk combined = current.merge(paragraphSeed);
            if (combined.charLen() <= TARGET_MAX) {
                current = combined;
            } else {
                chunks.add(current);
                current = paragraphSeed.withPrefix(tail(current.content()));
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return rebalanceSmallChunks(chunks);
    }

    private List<String> paragraphsOf(String text) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                if (!current.isEmpty()) {
                    paragraphs.add(current.toString().strip());
                    current.setLength(0);
                }
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line.strip());
        }
        if (!current.isEmpty()) {
            paragraphs.add(current.toString().strip());
        }
        return paragraphs;
    }

    private List<SeedChunk> splitLongParagraph(String text, SeedChunk source) {
        return splitByWindow(new SeedChunk(text, source.headingPath(), source.pageStart(), source.pageEnd()), TARGET_MAX);
    }

    private List<SeedChunk> splitByWindow(SeedChunk source, int windowMax) {
        List<SeedChunk> chunks = new ArrayList<>();
        String text = source.content();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + windowMax, text.length());
            if (end < text.length() && text.length() - (end - OVERLAP_CHARS) < MIN_CHARS) {
                end = Math.max(start + MIN_CHARS, text.length() + OVERLAP_CHARS - MIN_CHARS);
            }
            String piece = text.substring(start, end).strip();
            if (!piece.isBlank()) {
                chunks.add(new SeedChunk(piece, source.headingPath(), source.pageStart(), source.pageEnd()));
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return chunks;
    }

    private List<SeedChunk> rebalanceSmallChunks(List<SeedChunk> chunks) {
        List<SeedChunk> result = new ArrayList<>();
        SeedChunk pendingSmall = null;
        for (SeedChunk chunk : chunks) {
            SeedChunk current = pendingSmall == null ? chunk : pendingSmall.merge(chunk);
            pendingSmall = null;
            if (current.charLen() > MAX_CHARS) {
                result.addAll(splitByWindow(current, MAX_CHARS));
                continue;
            }
            if (current.charLen() >= MIN_CHARS) {
                result.add(current);
                continue;
            }
            if (result.isEmpty()) {
                pendingSmall = current;
            } else {
                mergeSmallIntoPrevious(result, current);
            }
        }
        if (pendingSmall != null) {
            if (result.isEmpty()) {
                result.add(pendingSmall);
            } else {
                mergeSmallIntoPrevious(result, pendingSmall);
            }
        }
        return result;
    }

    private void mergeSmallIntoPrevious(List<SeedChunk> result, SeedChunk small) {
        SeedChunk previous = result.get(result.size() - 1);
        SeedChunk combined = previous.merge(small);
        if (combined.charLen() <= MAX_CHARS) {
            result.set(result.size() - 1, combined);
        } else {
            List<SeedChunk> redistributed = redistributeTrailingPair(combined);
            if (!redistributed.isEmpty()) {
                result.remove(result.size() - 1);
                result.addAll(redistributed);
            } else {
                result.add(small);
            }
        }
    }

    private List<SeedChunk> redistributeTrailingPair(SeedChunk combined) {
        String text = combined.content();
        int firstEnd = Math.min(MAX_CHARS, text.length() + OVERLAP_CHARS - MIN_CHARS);
        if (firstEnd < MIN_CHARS || firstEnd >= text.length()) {
            return List.of();
        }
        int secondStart = Math.max(0, firstEnd - OVERLAP_CHARS);
        SeedChunk first = new SeedChunk(text.substring(0, firstEnd), combined.headingPath(), combined.pageStart(), combined.pageEnd());
        SeedChunk second = new SeedChunk(text.substring(secondStart), combined.headingPath(), combined.pageStart(), combined.pageEnd());
        if (first.charLen() < MIN_CHARS || first.charLen() > MAX_CHARS
                || second.charLen() < MIN_CHARS || second.charLen() > MAX_CHARS) {
            return List.of();
        }
        return List.of(first, second);
    }

    private String tail(String text) {
        if (text.length() <= OVERLAP_CHARS) {
            return text;
        }
        return text.substring(text.length() - OVERLAP_CHARS).strip();
    }

    private record Paragraph(String text, Integer pageStart, Integer pageEnd) {
    }

    private record SeedChunk(String content, String headingPath, Integer pageStart, Integer pageEnd) {

        SeedChunk {
            content = content == null ? "" : content.strip();
            headingPath = headingPath == null || headingPath.isBlank() ? null : headingPath;
        }

        int charLen() {
            return content.length();
        }

        SeedChunk merge(SeedChunk other) {
            return new SeedChunk(
                    joinContent(content, other.content()),
                    commonHeading(headingPath, other.headingPath()),
                    minPage(pageStart, other.pageStart()),
                    maxPage(pageEnd, other.pageEnd())
            );
        }

        SeedChunk withPrefix(String prefix) {
            if (prefix == null || prefix.isBlank()) {
                return this;
            }
            return new SeedChunk(joinContent(prefix, content), headingPath, pageStart, pageEnd);
        }

        private static String joinContent(String left, String right) {
            if (left == null || left.isBlank()) {
                return right == null ? "" : right.strip();
            }
            if (right == null || right.isBlank()) {
                return left.strip();
            }
            return left.strip() + "\n\n" + right.strip();
        }

        private static String commonHeading(String left, String right) {
            if (left == null) {
                return right;
            }
            if (right == null || left.equals(right)) {
                return left;
            }
            String[] leftParts = left.split(" > ");
            String[] rightParts = right.split(" > ");
            List<String> common = new ArrayList<>();
            for (int i = 0; i < Math.min(leftParts.length, rightParts.length); i++) {
                if (!leftParts[i].equals(rightParts[i])) {
                    break;
                }
                common.add(leftParts[i]);
            }
            return common.isEmpty() ? left : String.join(" > ", common);
        }

        private static Integer minPage(Integer left, Integer right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return Math.min(left, right);
        }

        private static Integer maxPage(Integer left, Integer right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return Math.max(left, right);
        }
    }
}
