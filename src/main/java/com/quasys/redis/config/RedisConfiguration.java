package com.quasys.redis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.SslOptions;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import java.util.Collections;
import java.util.List;

/**
 * Enterprise Redis Configuration using Lettuce Client.
 *
 * Best practices demonstrated:
 * 1. Low-level TCP Socket tuning (TCP Keep-Alive, TCP No-Delay, connect timeout)
 * 2. TLS/SSL & SNI (Server Name Indication) for secure Redis Enterprise / OpenShift routes
 * 3. Adaptive & periodic Cluster Topology Refresh (essential for Redis Enterprise cluster environments)
 * 4. Auto-reconnection & connection validation (ping before activate)
 * 5. Production-grade JSON serialization with Java 8/modern date/time support
 */
import io.lettuce.core.resource.Delay;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

    private final RedisConfigurationProperties properties;

    public RedisConfiguration(RedisConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Configures the reconnect delay strategy on Lettuce ClientResources.
     * Uses exponential backoff (starting at 100ms) with a configurable upper ceiling (reconnectDelayMax).
     */
    @Bean
    public ClientResourcesBuilderCustomizer clientResourcesBuilderCustomizer() {
        return clientResourcesBuilder -> {
            Duration maxDelay = properties.getReconnectDelayMax();
            log.info("Configuring Lettuce reconnect delay strategy (exponential backoff up to {})...", maxDelay);
            clientResourcesBuilder.reconnectDelay(
                    Delay.exponential(Duration.ofMillis(100), maxDelay, 2, TimeUnit.MILLISECONDS)
            );
        };
    }

    /**
     * Customizes Lettuce client configuration with production best practices:
     * - Socket keep-alive to avoid connection drops across firewalls/proxies.
     * - TLS/SSL & SNI (Server Name Indication) configuration for OpenShift/Ingress routes.
     * - Cluster topology refresh (adaptive triggers and periodic refresh) for seamless failover.
     * - Auto-reconnect and ping-before-activate to ensure healthy connections.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer(
            RedisProperties redisProperties) {
        return clientConfigurationBuilder -> {
            log.info("Configuring Lettuce client with production best practices...");

            // Determine SSL requirement from spring.data.redis.ssl
            boolean isSsl = properties.getSsl().isEnabled()
                    || (redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled());

            if (isSsl) {
                log.info("Enabling SSL/TLS for Redis connection.");
                var sslBuilder = clientConfigurationBuilder.useSsl();
                if (properties.getSsl().isDisablePeerVerification()) {
                    log.warn("Disabling SSL peer verification for Redis connection (test / self-signed certificate mode).");
                    sslBuilder.disablePeerVerification();
                }
            }

            // Socket tuning
            SocketOptions socketOptions = SocketOptions.builder()
                    .keepAlive(properties.getSocket().isKeepAlive())
                    .tcpNoDelay(properties.getSocket().isTcpNoDelay())
                    .connectTimeout(properties.getSocket().getConnectTimeout())
                    .build();

            // SSL & SNI configuration
            SslOptions sslOptions = null;
            if (isSsl) {
                SslOptions.Builder sslOptionsBuilder = SslOptions.builder().jdkSslProvider();

                if (properties.getSsl().isUseSni()) {
                    String sniHost = StringUtils.hasText(properties.getSsl().getSniServerName())
                            ? properties.getSsl().getSniServerName()
                            : redisProperties.getHost();

                    if (StringUtils.hasText(sniHost)) {
                        log.info("Configuring TLS SNI (Server Name Indication) for hostname: {}", sniHost);
                        List<SNIServerName> serverNames = Collections.singletonList(new SNIHostName(sniHost));
                        sslOptionsBuilder.sslParameters(() -> {
                            SSLParameters parameters = new SSLParameters();
                            parameters.setServerNames(serverNames);
                            return parameters;
                        });
                    }
                }

                if (properties.getSsl().isDisablePeerVerification()) {
                    sslOptionsBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                }

                sslOptions = sslOptionsBuilder.build();
            }

            if (properties.getCluster().isEnabled()) {
                log.info("Cluster mode is enabled. Applying ClusterTopologyRefreshOptions.");
                ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                        .enableAllAdaptiveRefreshTriggers() // Refreshes on MOVED, ASK, PERSISTENT_RECONNECTS, UNCOVERED_SLOT
                        .enablePeriodicRefresh(properties.getTopologyRefresh().getPeriodicRefreshInterval())
                        .dynamicRefreshSources(properties.getTopologyRefresh().isDynamicRefreshSources())
                        .build();

                ClusterClientOptions.Builder clusterClientOptionsBuilder = ClusterClientOptions.builder()
                        .socketOptions(socketOptions)
                        .autoReconnect(properties.isAutoReconnect())
                        .pingBeforeActivateConnection(properties.isPingBeforeActivate())
                        .topologyRefreshOptions(topologyRefreshOptions);

                if (sslOptions != null) {
                    clusterClientOptionsBuilder.sslOptions(sslOptions);
                }

                clientConfigurationBuilder.clientOptions(clusterClientOptionsBuilder.build());
            } else {
                log.info("Single endpoint / Standalone proxy mode is configured.");
                ClientOptions.Builder clientOptionsBuilder = ClientOptions.builder()
                        .socketOptions(socketOptions)
                        .autoReconnect(properties.isAutoReconnect())
                        .pingBeforeActivateConnection(properties.isPingBeforeActivate());

                if (sslOptions != null) {
                    clientOptionsBuilder.sslOptions(sslOptions);
                }

                clientConfigurationBuilder.clientOptions(clientOptionsBuilder.build());
            }
        };
    }

    /**
     * Best practice RedisTemplate configuration:
     * - Keys: UTF-8 String serializer
     * - Values: Generic Jackson 2 JSON serializer with JavaTimeModule support
     * - Hash Keys: UTF-8 String serializer
     * - Hash Values: Generic Jackson 2 JSON serializer
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper with JavaTimeModule for modern date/time handling
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key serializers
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value serializers
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
