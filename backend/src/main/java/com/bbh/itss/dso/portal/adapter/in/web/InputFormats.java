package com.bbh.itss.dso.portal.adapter.in.web;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
final class InputFormats {

    static final String URL = "^(https?://[^\\s$`\"\\\\]+)?$";
    static final String URL_MESSAGE = "must be an http or https URL without spaces, double quotes, backslashes, $"
            + " or backticks";
    static final String HOST = "^[A-Za-z0-9.-]*$";
    static final String HOST_MESSAGE = "must be a host name";
    static final String SHELL_SAFE = "^[A-Za-z0-9._/*+@:=,~-]*$";
    static final String SHELL_SAFE_MESSAGE = "may contain letters, digits and . _ / * + @ : = , ~ - only:"
            + " the library puts it unquoted into a shell command";
    static final String SHELL_SAFE_URL = "^(https?://[A-Za-z0-9._/+@:=,~%-]+)?$";
    static final String SHELL_SAFE_URL_MESSAGE = "must be an http or https URL with letters, digits and"
            + " . _ / + @ : = , ~ % - only: the library puts it unquoted into a shell command";
    static final String POWERSHELL_PATH = "^[A-Za-z0-9._/\\\\-]*$";
    static final String POWERSHELL_PATH_MESSAGE = "may contain letters, digits and . _ / \\ - only:"
            + " the library runs it unquoted in PowerShell";
    static final String IMAGE_TAG = "^[A-Za-z0-9._:/@+-]*$";
    static final String IMAGE_TAG_MESSAGE = "may contain letters, digits and . _ : / @ + - only, like the tags the"
            + " library copies from the security pipeline";
    static final String FOLDER = "^[^,']{1,300}$";
    static final String FOLDER_MESSAGE = "one folder per entry, without commas or quotes";
    static final String JOB_PATH = "^((?!.*\\.\\.)[A-Za-z0-9._ /-]+)?$";
    static final String JOB_PATH_MESSAGE = "must be a Jenkins job path such as DevSecOps/CertScanner-extended,"
            + " with letters, digits, spaces and . _ / - but no '..'";
    static final String GIT_URL = "^(https?://\\S+|ssh://\\S+|git@\\S+)?$";
    static final String MODULE = "^[A-Za-z0-9._/-]{1,100}$";
    static final String MODULE_MESSAGE = "must be a module folder name";
    static final String NO_WHITESPACE = "^\\S*$";
    static final String NO_WHITESPACE_MESSAGE = "must not contain whitespace";
}
