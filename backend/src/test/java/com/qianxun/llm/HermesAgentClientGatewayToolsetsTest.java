package com.qianxun.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HermesAgentClientGatewayToolsetsTest {

    @Test
    void parseEnabledGatewayToolsets_shouldReadDataArray() throws Exception {
        HermesAgentClient client = new HermesAgentClient(new ObjectMapper(), new QianxunProperties());
        List<String> names = client.parseEnabledGatewayToolsets("""
                {"object":"list","platform":"api_server","data":[
                  {"name":"web","enabled":true,"tools":["web_search"]},
                  {"name":"browser","enabled":false}
                ]}
                """);
        assertThat(names).containsExactly("web");
    }
}
