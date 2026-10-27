package com.guerram.DespensaFamily;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Sin esto, levantar el contexto en un test abriria el navegador, pondria un
// icono en la bandeja y dejaria un respaldo en la maquina que corre los tests.
@SpringBootTest(properties = {
		"despensa.escritorio=false",
		"despensa.respaldos.al-arrancar=false"
})
class DespensaFamilyApplicationTests {

	@Test
	void contextLoads() {
	}

}
