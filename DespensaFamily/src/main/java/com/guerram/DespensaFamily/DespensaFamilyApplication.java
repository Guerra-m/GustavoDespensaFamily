package com.guerram.DespensaFamily;

import com.guerram.DespensaFamily.config.AppPaths;
import com.guerram.DespensaFamily.config.ErrorDeArranque;
import com.guerram.DespensaFamily.config.Escritorio;
import com.guerram.DespensaFamily.config.ModoEscritorio;
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

		// En modo ventana el navegador no se toca: la ventana la pone el
		// programa de escritorio, que ya se encarga de traerla al frente.
		ModoEscritorio modo = Escritorio.modoDeLosArgumentos(
				args,
				Escritorio.modoConfigurado(
						carpetaDeDatos.resolve("config.properties"),
						ModoEscritorio.NAVEGADOR));

		if (Escritorio.yaEstaCorriendo(puerto)) {
			System.out.println("DespensaFamily ya estaba abierta.");
			if (modo.abreElNavegador()) {
				Escritorio.abrirNavegador(puerto);
			}
			return;
		}

		try {
			SpringApplication.run(DespensaFamilyApplication.class, args);
		} catch (RuntimeException e) {
			// La ventana de escritorio no ve esta consola: si el motivo es uno
			// que el usuario puede resolver (una base de una version mas nueva),
			// se lo deja escrito para que lo muestre en vez de "no respondio".
			ErrorDeArranque.anotar(e, carpetaDeDatos.resolve("logs"));
			throw e;
		}
	}

}
