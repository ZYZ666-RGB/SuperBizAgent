package org.example.memory.graph;

import org.example.memory.MemoryProperties;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "memory.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Driver neo4jDriver(MemoryProperties memoryProperties) {
        MemoryProperties.Graph graph = memoryProperties.getGraph();
        return GraphDatabase.driver(
                graph.getNeo4jUri(),
                AuthTokens.basic(graph.getUsername(), graph.getPassword()));
    }
}
