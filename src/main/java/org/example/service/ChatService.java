package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    @Value("${spring.ai.dashscope.api-key:YOUR_DASHSCOPE_API_KEY}")
    private String apiKey;

    @Value("${rag.model:qwen3-max}")
    private String modelName;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("YOUR_");
    }

    public String ask(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("请先在 application.yml 中将 YOUR_DASHSCOPE_API_KEY 替换为自己的 DashScope API Key");
        }

        DashScopeApi api = DashScopeApi.builder().apiKey(apiKey).build();
        DashScopeChatModel model = DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(modelName)
                        .withTemperature(0.2)
                        .withMaxToken(4000)
                        .withTopP(0.8)
                        .build())
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("research_assistant")
                .model(model)
                .systemPrompt(systemPrompt)
                .build();
        try {
            return agent.call(userPrompt).getText();
        } catch (Exception e) {
            throw new IllegalStateException("大模型调用失败: " + e.getMessage(), e);
        }
    }
}
