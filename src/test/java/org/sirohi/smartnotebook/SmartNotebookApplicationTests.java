package org.sirohi.smartnotebook;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Disabled("Requires running Postgres and Ollama — enable for integration testing")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SmartNotebookApplicationTests {

    @Test
    void contextLoads() {
    }

}
