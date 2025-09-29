package com.resumebuddy.model;

public enum ResumeStatus {
    UPLOADED("File uploaded successfully"),
    PARSING("Document parsing in progress"),
    PARSED("Document parsed successfully"),
    FAILED("Parsing failed");

    private final String description;

    ResumeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}