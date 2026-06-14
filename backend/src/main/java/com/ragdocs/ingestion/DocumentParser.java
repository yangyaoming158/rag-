package com.ragdocs.ingestion;

import com.ragdocs.domain.Document;

import java.io.InputStream;

public interface DocumentParser {

    ParsedDocument parse(Document document, InputStream inputStream);
}
