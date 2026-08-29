package com.guerram.DespensaFamily;

import com.guerram.DespensaFamily.config.AppPaths;
import com.guerram.DespensaFamily.config.Escritorio;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

@SpringBootApplication
public class DespensaFamilyApplication {

	// La carpeta de datos la prepara AppPathsEnvironmentPostProcessor, que
	// corre antes de que Spring resuelva ${despensa.db} en la URL de la base.
	public static void main(String[] args) {
		// Instancia unica: si la app ya esta abierta, hacer doble clic en el
		// icono tiene que traerla al frente, no levantar un segundo servidor
		// peleando por el mismo puerto (y por el mismo archivo de base).
		Path carpetaDeDatos = AppPaths.bootstrap();
		int puerto = Escritorio.puertoDeLosArgumentos(
				args,
				Escritorio.puertoConfigurado(
						carpetaDeDatos.resolve("config.properties"),
						Escritorio.PUERTO_POR_DEFECTO));

		if (Escritorio.yaEstaCorriendo(puerto)) {
			System.out.println("DespensaFamily ya estaba abierta. Abriendo el navegador.");
			Escritorio.abrirNavegador(puerto);
			return;
		}

		SpringApplication.run(DespensaFamilyApplication.class, args);
	}

}
