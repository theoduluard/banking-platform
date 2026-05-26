package com.solarisbank.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke-test: verifies the Spring context loads without errors.
 * Uses the "test" profile which substitutes H2 for PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
