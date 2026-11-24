package com.nibras.pim.service.localnotes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** quick-tasks.md for the dashboard: open tasks kept, ticked ones (and what hangs under them) dropped. */
class QuickTasksTest {

    @Test
    void dropsTickedTasksTheirSubItemsAndTheFilesComment() {
        String file = """
                # Quick tasks

                <!-- to-dos captured without leaving knotes. Plain markdown: edit anywhere. -->
                - [ ] Open one
                - [x] Done one
                  - its sub-item
                - [X] Done too
                - [ ] جمل جوابية
                  - [x] a ticked sub-task
                  - an open detail
                """;
        assertThat(QuickTasksService.openTasks(file)).isEqualTo("""
                # Quick tasks

                - [ ] Open one
                - [ ] جمل جوابية
                  - an open detail""");
    }
}
