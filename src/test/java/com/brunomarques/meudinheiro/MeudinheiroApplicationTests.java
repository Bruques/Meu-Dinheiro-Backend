package com.brunomarques.meudinheiro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"gemini.api.key=test",
		"whatsapp.api.phone-id=test",
		"whatsapp.api.token=test",
		"whatsapp.app.secret=test",
		"whatsapp.verify.token=test",
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class MeudinheiroApplicationTests {

	@Test
	void contextLoads() {
	}

}
