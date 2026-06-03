package com.cx.ai_agent_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "langchain4j.open-ai.chat-model.api-key=test-key")
class AiAgentBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
