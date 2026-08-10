package com.diet.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 模型配置（DeepSeek API，OpenAI 兼容协议）。
 */
@Configuration
public class DietAgentScopeConfig {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    @Value("${agentscope.deepseek.api-key:}")
    private String apiKey;

    @Value("${diet.llm.main-model:deepseek-v4-pro}")
    private String mainModelName;

    @Value("${diet.llm.light-model:deepseek-v4-flash}")
    private String lightModelName;

    @Bean("DietMainChatModel")
    public Model DietMainChatModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(mainModelName)
                .build();
    }

    @Bean("DietLightChatModel")
    public Model DietLightChatModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(lightModelName)
                .build();
    }
}




