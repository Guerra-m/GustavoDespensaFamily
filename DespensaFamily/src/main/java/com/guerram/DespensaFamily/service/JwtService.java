package com.guerram.DespensaFamily.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    // Lo genera SecretoJwt en el primer arranque y vive en config.properties,
    // dentro de la carpeta de datos: uno distinto por instalacion. Ya no hay
    // ningun valor por defecto en el repositorio. Se puede pisar con la
    // variable de entorno JWT_SECRET.
    @Value("${jwt.secret}")
    private String secret;

    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Los permisos quedan grabados en el token, no se relee la base en cada
    // pedido (mismo criterio que el rol): si el dueño le prende un permiso a
    // alguien que ya tiene la sesión abierta, no se ve hasta el próximo
    // login. Es el mismo tradeoff que ya existía para el rol.
    public String generateToken(String username, String rol, boolean puedeGestionarProductos, boolean puedeVerCaja) {
        return Jwts.builder()
                .setSubject(username)
                .addClaims(Map.of(
                        "rol", rol,
                        "stock", puedeGestionarProductos,
                        "caja", puedeVerCaja))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
