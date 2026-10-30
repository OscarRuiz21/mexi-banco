package mx.mexibanco.cuenta;

import org.springframework.context.annotation.Configuration;

/**
 * v11: la cache de GET /cuentas/{clabe} en Redis, con el patron cache-aside.
 *
 * Que va aqui (lab S11, Parte 1):
 *  - encender la abstraccion de cache de Spring;
 *  - decirle al RedisCacheManager que Spring Boot ya arma (por spring-boot-starter-data-redis) tres
 *    cosas de la cache "cuentas": cuanto vive cada entrada (mexibanco.cache.ttl, 30 s), que el valor
 *    se guarde como JSON (para leerlo con redis-cli) y que sea transactionAware(), para que el borrado
 *    ocurra DESPUES del commit y no antes.
 *
 * Lo que ya esta listo: el host y el puerto de Redis (spring.data.redis en application.yml) y
 * spring.cache.type, que con CACHE_TIPO=none apaga la cache sin tocar codigo.
 */
// TODO S11: @EnableCaching, sin el, @Cacheable y @CacheEvict se ignoran sin avisar.
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	// TODO S11: un @Bean RedisCacheManagerBuilderCustomizer que a la cache CUENTAS le ponga entryTtl(ttl), valores con Jackson2JsonRedisSerializer<>(json, CuentaVista.class) y llame a transactionAware().
}
