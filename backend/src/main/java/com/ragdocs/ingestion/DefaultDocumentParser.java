package com.ragdocs.ingestion;

import com.ragdocs.domain.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultDocumentParser implements DocumentParser {
    private final TextCleaner cleaner;
    private final Tika tika;

    public DefaultDocumentParser(TextCleaner cleaner) {
        this.cleaner = cleaner;
        this.tika = new Tika();
    }

    @Override
    public ParsedDocument parse(Document document, InputStream inputStream) {
        DocumentKind kind = kindOf(document);
        try {
            return switch (kind) {
                case MARKDOWN -> parseMarkdown(inputStream);
                case TEXT -> parseText(inputStream);
                case PDF -> parsePdf(inputStream);
            };
        } catch (IOException ex) {
            throw new IngestionException("文档解析失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedDocument parseMarkdown(InputStream inputStream) throws IOException {
        String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        String cleaned = cleaner.clean(raw);
        cleaner.validateQuality(cleaned);
        return new ParsedDocument(DocumentKind.MARKDOWN, parseMarkdownBlocks(cleaned));
    }

    private ParsedDocument parseText(InputStream inputStream) throws IOException {
        String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        String cleaned = cleaner.clean(raw);
        cleaner.validateQuality(cleaned);
        return new ParsedDocument(DocumentKind.TEXT, List.of(new ParsedBlock(cleaned, null, null, null)));
    }

    private ParsedDocument parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument pdf = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedBlock> blocks = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String cleaned = cleaner.clean(stripper.getText(pdf));
                if (!cleaned.isBlank()) {
                    blocks.add(new ParsedBlock(cleaned, null, page, page));
                    fullText.append(cleaned).append("\n\n");
                }
            }
            String text = fullText.toString().strip();
            cleaner.validateQuality(text);
            if (blocks.isEmpty()) {
                throw new IngestionException("PDF 未解析出可用文本");
            }
            return new ParsedDocument(DocumentKind.PDF, blocks);
        } catch (IOException ex) {
            throw new IngestionException("PDF 解析失败: " + ex.getMessage(), ex);
        }
    }

    private List<ParsedBlock> parseMarkdownBlocks(String cleaned) {
        List<ParsedBlock> blocks = new ArrayList<>();
        String[] lines = cleaned.split("\n", -1);
        String[] headings = new String[6];
        String currentHeading = null;
        StringBuilder content = new StringBuilder();

        for (String line : lines) {
            Heading heading = parseHeading(line);
            if (heading != null) {
                flushMarkdownBlock(blocks, content, currentHeading);
                headings[heading.level() - 1] = heading.title();
                for (int i = heading.level(); i < headings.length; i++) {
                    headings[i] = null;
                }
                currentHeading = joinHeadings(headings);
                continue;
            }
            content.append(line).append('\n');
        }
        flushMarkdownBlock(blocks, content, currentHeading);
        if (blocks.isEmpty()) {
            blocks.add(new ParsedBlock(cleaned, null, null, null));
        }
        return blocks;
    }

    private void flushMarkdownBlock(List<ParsedBlock> blocks, StringBuilder content, String headingPath) {
        String text = cleaner.clean(content.toString());
        content.setLength(0);
        if (!text.isBlank()) {
            blocks.add(new ParsedBlock(text, headingPath, null, null));
        }
    }

    private Heading parseHeading(String line) {
        int level = 0;
        while (level < line.length() && level < 6 && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level >= line.length() || line.charAt(level) != ' ') {
            return null;
        }
        String title = line.substring(level + 1).strip();
        if (title.isBlank()) {
            return null;
        }
        return new Heading(level, title);
    }

    private String joinHeadings(String[] headings) {
        List<String> values = new ArrayList<>();
        for (String heading : headings) {
            if (heading != null && !heading.isBlank()) {
                values.add(heading);
            }
        }
        return values.isEmpty() ? null : String.join(" > ", values);
    }

    private DocumentKind kindOf(Document document) {
        String filename = document.originalFilename().toLowerCase();
        if (filename.endsWith(".md") || filename.endsWith(".markdown")) {
            return DocumentKind.MARKDOWN;
        }
        if (filename.endsWith(".pdf")) {
            return DocumentKind.PDF;
        }
        if (filename.endsWith(".txt")) {
            return DocumentKind.TEXT;
        }
        try {
            String detected = tika.detect(document.originalFilename());
            if ("application/pdf".equals(detected)) {
                return DocumentKind.PDF;
            }
        } catch (RuntimeException ignored) {
            // Extension validation already ran during upload; default to text below.
        }
        return DocumentKind.TEXT;
    }

    private record Heading(int level, String title) {
    }
}
