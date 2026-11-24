package com.nibras.pim;

import com.nibras.pim.service.filesearch.FileIndexService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search → Files → Download: a found file comes back byte for byte as an attachment, and only a
 * file the index holds — the path parameter must not reach anything else on the server.
 */
class FileDownloadTests extends AuthTestSupport {

    @MockitoBean
    FileIndexService index;

    @Test
    void anIndexedFileDownloadsByteForByte() throws Exception {
        Path f = Files.createTempFile("نتيجة-", ".bin");
        byte[] bytes = {0, 1, 2, (byte) 0xFF, 10, 13, 0x7F};
        Files.write(f, bytes);
        when(index.isIndexed(f.toString())).thenReturn(true);

        var res = mvc.perform(get("/api/search/files/download")
                        .header("Authorization", bearer())
                        .param("path", f.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(res.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(res.getContentType()).isEqualTo("application/octet-stream");
        assertThat(res.getHeader("Content-Disposition")).startsWith("attachment;").contains("filename*=UTF-8''");
        Files.deleteIfExists(f);
    }

    @Test
    void aFileOutsideTheIndexIsRefused() throws Exception {
        when(index.isIndexed(anyString())).thenReturn(false);
        mvc.perform(get("/api/search/files/download")
                        .header("Authorization", bearer())
                        .param("path", "/etc/passwd"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(get("/api/search/files/download").param("path", "/etc/passwd"))
                .andExpect(status().isUnauthorized());
    }
}
