package com.qianxun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI qianxunOpenApi() {
        final String bearer = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("千寻智能体平台 API")
                        .description("Claude Code SDK 对外 REST：智能体管理、流式对话、多智能体委派、技能、工具、MCP/插件、运行观测。")
                        .version("1.1.0")
                        .contact(new Contact().name("QianXun").email("support@qianxun.local"))
                        .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("/").description("当前主机"))
                .components(new Components()
                        .addSecuritySchemes(bearer, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /QianXunService/auth/login 获取 token")))
                .addSecurityItem(new SecurityRequirement().addList(bearer));
    }
}
