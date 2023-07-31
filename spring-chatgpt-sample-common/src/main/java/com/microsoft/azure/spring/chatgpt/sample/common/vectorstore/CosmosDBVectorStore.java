package com.microsoft.azure.spring.chatgpt.sample.common.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.AggregateIterable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@EnableMongoRepositories(basePackages = "com.microsoft.azure.spring.chatgpt.sample.common.vectorstore")
@Component
@Slf4j
public class CosmosDBVectorStore implements VectorStore {

    private final VectorStoreData data;

    private MongoTemplate mongoTemplate;

    public CosmosDBVectorStore(MongoTemplate mongoTemplate) {
        this.data = new VectorStoreData();
        this.mongoTemplate = mongoTemplate;
    }


    @Override
    public void saveDocument(String key, DocEntry doc) {
        mongoTemplate.save(doc);
    }

    @Override
    public DocEntry getDocument(String key) {
        var doc = mongoTemplate.findById(key, DocEntry.class);
        return doc;
    }

    @Override
    public void removeDocument(String key) {
        mongoTemplate.remove(key);
    }

    @Override
    public List<DocEntry> searchTopKNearest(List<Double> embedding, int k) {
        return searchTopKNearest(embedding, k, 0);
    }

    @Override
    public List<DocEntry> searchTopKNearest(List<Double> embedding, int k, double cutOff) {
        // perform vector search in Cosmos DB Mongo API - vCore
        String command = "{\"$search\":{\"cosmosSearch\":{\"vector\":" + embedding + ",\"path\":\"embedding\",\"k\":" + k + "}}}\"";
        Document bsonCmd = Document.parse(command);
        var db = mongoTemplate.getDb();
        AggregateIterable<Document> mongoresult = db.getCollection("vectorstore").aggregate(List.of(bsonCmd));
        List<Document> docs = new ArrayList<>();
        for (Document doc : mongoresult) {
            docs.add(doc);
        }
        //mongoresult.into(docs);
        List<DocEntry> result = new ArrayList<>();
        for (Document doc : docs) {
            String id = doc.getString("id");
            String hash = doc.getString("hash");
            String text = doc.getString("text");
            List<Double> embedding1 = (List<Double>) doc.get("embedding");
            DocEntry docEntry = new DocEntry(id, hash, text, embedding1);
            result.add(docEntry);
        }
        return result;
    }

    public void saveToJsonFile(String filePath) {
        var objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try (var fileWriter = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            objectWriter.writeValue(fileWriter, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createVectorIndex() {
        String bsonCmd = "{\"createIndexes\":\"vectorstore\",\"indexes\":" +
                "[{\"name\":\"vectorsearch\",\"key\":{\"embedding\":\"cosmosSearch\"},\"cosmosSearchOptions\":" +
                "{\"kind\":\"vector-ivf\",\"numLists\":100,\"similarity\":\"COS\",\"dimensions\":1536}}]}";
        log.info("creating vector index in Cosmos DB Mongo vCore...");
        try {
            mongoTemplate.executeCommand(bsonCmd);
        } catch (Exception e) {
            log.warn("Failed to create vector index in Cosmos DB Mongo vCore", e);
        }
    }

    public List<MongoEntity> loadFromJsonFile(String filePath) {
        var reader = new ObjectMapper().reader();
        try {
            var data = reader.readValue(new File(filePath), VectorStoreData.class);
            List<DocEntry> list = new ArrayList<DocEntry>(data.store.values());
            List<MongoEntity> mongoEntities = new ArrayList<>();
            for (DocEntry docEntry : list) {
                MongoEntity doc = new MongoEntity(docEntry.getId(), docEntry.getHash(), docEntry.getText(), docEntry.getEmbedding());
                mongoEntities.add(doc);
            }
            //insert to Cosmos DB Mongo API - vCore
            Document FirstDocFound = mongoTemplate.getDb().getCollection("vectorstore").find().first();
            if (FirstDocFound == null) {
                try {
                    log.info("Saving all documents to Cosmos DB Mongo vCore");
                    mongoTemplate.insertAll(mongoEntities);
                } catch (Exception e) {
                    log.warn("Failed to insertAll documents to Cosmos DB Mongo vCore, attempting individual upserts", e);
                    for (MongoEntity mongoEntity : mongoEntities) {
                        log.info("Saving document {} to mongoDB", mongoEntity.getId());
                        try {
                            mongoTemplate.save(mongoEntity);
                        } catch (Exception ex) {
                            log.warn("Failed to upsert document {} to mongoDB", mongoEntity.getId(), ex);
                        }
                    }
                }
                createVectorIndex();
            }
            return mongoEntities;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Setter
    @Getter
    private static class VectorStoreData {
        private Map<String, DocEntry> store = new ConcurrentHashMap<>();
    }
}
