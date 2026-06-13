package com.ragdocs.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

class MultipartFileStub implements MultipartFile {
    private final String originalFilename;
    private final String contentType;
    private final long size;

    MultipartFileStub(String originalFilename, String contentType, long size) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        throw new UnsupportedOperationException("not used in validator tests");
    }
}
