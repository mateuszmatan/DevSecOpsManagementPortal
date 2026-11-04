package com.bbh.itss.dso.portal.domain.discovery;

import java.util.List;
import java.util.Optional;

public interface RepoFileTree {

    boolean fileExists(String path);

    boolean directoryExists(String path);

    List<String> listFiles(String directoryPath);

    Optional<String> readFile(String path);
}
