package com.isp.zoho.notifier.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZohoNoteRequest(
    @JsonProperty("Note_Title") String noteTitle,
    @JsonProperty("Note_Content") String noteContent
) {}
