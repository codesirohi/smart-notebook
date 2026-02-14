package org.sirohi.smartnotebook;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Disabled("Blocked by Spring AI 2.0.0-M2 bug: ClassNotFoundException org.springframework.core.retry.RetryTemplate")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SmartNotebookApplicationTests {

    @Test
    void contextLoads() {
    }

}
