package com.nibras.pim.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The old app's letters for a note's linked record, read and written the same way. */
class RecordLinkCodesTest {

    @Test
    void readsTheOldLettersLatinAndArabic() {
        assertThat(RecordLinkService.moduleOf("W")).isEqualTo("writings");
        assertThat(RecordLinkService.moduleOf("w")).isEqualTo("writings");
        assertThat(RecordLinkService.moduleOf("ك")).isEqualTo("writings");
        assertThat(RecordLinkService.moduleOf("S")).isEqualTo("contacts");
        assertThat(RecordLinkService.moduleOf("R")).isEqualTo("resources");
        assertThat(RecordLinkService.moduleOf("?")).isNull();
        assertThat(RecordLinkService.moduleOf(null)).isNull();
    }

    @Test
    void writesTheFirstLetterAndRefusesWhatCannotBeLinked() {
        assertThat(RecordLinkService.codesOf("contacts").get(0)).isEqualTo("S");
        assertThat(RecordLinkService.codesOf("notes").get(0)).isEqualTo("N");
        assertThatThrownBy(() -> RecordLinkService.codesOf("sprints")).isInstanceOf(ResponseStatusException.class);
    }
}
