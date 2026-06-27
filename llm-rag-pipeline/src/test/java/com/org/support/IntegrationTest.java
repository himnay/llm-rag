package com.org.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests. Spins up real Postgres + OpenSearch via Testcontainers and
 * stubs the embedding model (see {@link FakeEmbeddingConfig}), so the full Spring context loads
 * with no external infrastructure and no OpenAI key.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} means: when a usable Docker environment
 * is present (e.g. CI), these tests run end-to-end; when it isn't, the whole class is <b>skipped</b>
 * rather than failed — so {@code mvn test} stays green on machines without Docker.</p>
 *
 * <p>Containers are {@code static} and shared across all subclasses (a single cached Spring
 * context). They start once and are reaped by Ryuk / JVM shutdown. OpenSearch runs single-node
 * with the security plugin disabled (test only).</p>
 */
@Import(FakeEmbeddingConfig.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"));

    static final GenericContainer<?> OPENSEARCH =
            new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.17.1"))
                    .withExposedPorts(9200)
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .waitingFor(Wait.forHttp("/_cluster/health").forPort(9200).forStatusCode(200))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    static {
        // Guard so a broken Docker setup can't throw from static init before the
        // disabledWithoutDocker condition skips the class.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            OPENSEARCH.start();
            MONGO.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void openSearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.opensearch.uris",
                () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
