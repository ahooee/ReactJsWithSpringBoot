package ir.linuxian.second.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConf {


    @Bean
    public OpenAPI OpenApiConf() {
        return new OpenAPI().
                info(new Info().title("my Second App").description("alaki neveshtamesh").version("0.0.1-SNAPSHOT"));
    }

    @Bean
    public OperationCustomizer customGlobalHeaders() {
        return (operation, handlerMethod) -> {
            // Prevent empty schema generation
            return operation;
        };
    }

}
