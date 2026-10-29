package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.adapter.in.web.ConfigFileParser.MAX_LENGTH;

/** A config file to read into new service forms, and the name for a file that holds one unnamed project. */
public record ConfigImportRequest(
        @NotBlank @Size(max = MAX_LENGTH) String content,
        @Size(max = 100) String serviceName) {
}
