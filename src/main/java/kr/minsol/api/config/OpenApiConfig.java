package kr.minsol.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway")
                        .version("1.0.0")
                        .description("API Gateway for minsol.kr")
                        .contact(new Contact()
                                .name("minsol.kr")
                                .email("support@minsol.kr")))
                .servers(List.of(
                        new Server().url("https://api.minsol.kr").description("Production Server"),
                        new Server().url("http://localhost:8080").description("Local Server")
                ));
    }
}

