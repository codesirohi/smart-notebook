package org.sirohi.smartnotebook;

import org.springframework.boot.SpringApplication;

public class TestSmartNotebookApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartNotebookApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
