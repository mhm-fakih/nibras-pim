package com.nibras.pim.web.dto;

import com.nibras.pim.service.RepositoryService;

import java.time.LocalDateTime;

/** A file in a record's repository folder, with the repo number it was found in. */
public record RepoFileDto(int repo, String name, boolean directory, long size, LocalDateTime modifiedAt) {

    public static RepoFileDto of(RepositoryService.RepoFile f) {
        return new RepoFileDto(f.repo(), f.name(), f.directory(), f.size(), f.modified());
    }
}
