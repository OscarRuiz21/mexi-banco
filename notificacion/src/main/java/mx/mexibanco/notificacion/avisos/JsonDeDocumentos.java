package mx.mexibanco.notificacion.avisos;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.bson.types.Decimal128;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * v11, ya resuelto: para que GET /notificaciones/{clabe} pueda devolver los documentos de MongoDB tal
 * cual (org.bson.Document, que es un Map), Jackson tiene que saber escribir un Decimal128, el tipo
 * decimal de BSON con el que se guarda el dinero. Sin esto saldria un objeto raro con sus bits.
 */
@Configuration
public class JsonDeDocumentos {

	@Bean
	Jackson2ObjectMapperBuilderCustomizer decimal128ComoNumero() {
		return builder -> builder.serializerByType(Decimal128.class, new StdSerializer<>(Decimal128.class) {
			@Override
			public void serialize(Decimal128 valor, JsonGenerator json, SerializerProvider proveedor) throws IOException {
				json.writeNumber(valor.bigDecimalValue());
			}
		});
	}
}
