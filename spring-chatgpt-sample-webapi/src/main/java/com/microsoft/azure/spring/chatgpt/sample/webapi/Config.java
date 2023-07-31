package com.microsoft.azure.spring.chatgpt.sample.webapi;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.azure.spring.chatgpt.sample.common.AzureOpenAIClient;
import com.microsoft.azure.spring.chatgpt.sample.common.ChatPlanner;
import com.microsoft.azure.spring.chatgpt.sample.common.vectorstore.CosmosDBVectorStore;
import com.microsoft.azure.spring.chatgpt.sample.common.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;

@Configuration
public class Config {


    @Value("${OPENAI_EMBEDDING_DEPLOYMENT_ID}")
    private String embeddingDeploymentId;

    @Value("${OPENAI_CHAT_DEPLOYMENT_ID}")
    private String chatDeploymentId;

    @Value("${OPENAI_ENDPOINT}")
    private String endpoint;

    @Value("${OPENAI_API_KEY}")
    private String apiKey;

    @Value("${vector-store.file}")
    private String vectorJsonFile;

    @Autowired
    private MongoTemplate mongoTemplate;


    @Bean
    public ChatPlanner planner(AzureOpenAIClient openAIClient, VectorStore vectorStore) {
        return new ChatPlanner(openAIClient, vectorStore);
    }

    @Bean
    public AzureOpenAIClient AzureOpenAIClient() {
        var innerClient = new OpenAIClientBuilder()
            .endpoint(endpoint)
            .credential(new AzureKeyCredential(apiKey))
            .buildClient();
        return new AzureOpenAIClient(innerClient, embeddingDeploymentId, chatDeploymentId);
    }

    @Bean
    public VectorStore vectorStore() throws IOException {
        CosmosDBVectorStore store = new CosmosDBVectorStore(mongoTemplate);
        String currentPath = new java.io.File(".").getCanonicalPath();;
        String path = currentPath+vectorJsonFile.replace(  "\\", "//");
        store.loadFromJsonFile(path);
        return store;
    }
}
