package com.example.gatewayapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:gateway-context;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"vision.baseUrl=http://localhost:8081",
		"vision.timeoutMillis=5000"
})
class GatewayApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
