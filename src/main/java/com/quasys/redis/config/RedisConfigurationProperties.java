package com.quasys.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Redis configuration properties under 'spring.data.redis'.
 * Unified with standard Spring Data Redis properties.
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisConfigurationProperties {

    /**
     * Cluster configuration options.
     */
    private ClusterProperties cluster = new ClusterProperties();

    /**
     * TLS/SSL and SNI configuration.
     */
    private SslProperties ssl = new SslProperties();

    /**
     * Socket and TCP level options.
     */
    private SocketProperties socket = new SocketProperties();

    /**
     * Cluster topology refresh configuration.
     */
    private TopologyRefreshProperties topologyRefresh = new TopologyRefreshProperties();

    /**
     * Automatic reconnection on disconnect.
     */
    private boolean autoReconnect = true;

    /**
     * Connection ping before activation.
     */
    private boolean pingBeforeActivate = true;

    /**
     * Maximum backoff delay between reconnection attempts (e.g. 5s, 10s, 30s).
     */
    private Duration reconnectDelayMax = Duration.ofSeconds(30);

    /**
     * Cache-specific TTLs in seconds (cacheName -> ttl).
     */
    private Map<String, Long> cacheTtls = new HashMap<>();

    /**
     * Configurable key prefix for Redis Enterprise ACL multi-tenancy (~prefix:*).
     * If configured, all cache and RedisTemplate keys are prefixed with this namespace.
     */
    private String keyPrefix = "quasys:";

    public Duration getReconnectDelayMax() {
        return reconnectDelayMax;
    }

    public void setReconnectDelayMax(Duration reconnectDelayMax) {
        this.reconnectDelayMax = reconnectDelayMax;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Returns the normalized key prefix.
     * Ensures trailing ':' if a non-empty prefix is provided.
     */
    public String getNormalizedKeyPrefix() {
        if (!org.springframework.util.StringUtils.hasText(keyPrefix)) {
            return "";
        }
        return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    }

    public ClusterProperties getCluster() {
        return cluster;
    }

    public void setCluster(ClusterProperties cluster) {
        this.cluster = cluster;
    }

    public SslProperties getSsl() {
        return ssl;
    }

    public void setSsl(SslProperties ssl) {
        this.ssl = ssl;
    }

    public SocketProperties getSocket() {
        return socket;
    }

    public void setSocket(SocketProperties socket) {
        this.socket = socket;
    }

    public TopologyRefreshProperties getTopologyRefresh() {
        return topologyRefresh;
    }

    public void setTopologyRefresh(TopologyRefreshProperties topologyRefresh) {
        this.topologyRefresh = topologyRefresh;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public boolean isPingBeforeActivate() {
        return pingBeforeActivate;
    }

    public void setPingBeforeActivate(boolean pingBeforeActivate) {
        this.pingBeforeActivate = pingBeforeActivate;
    }

    public Map<String, Long> getCacheTtls() {
        return cacheTtls;
    }

    public void setCacheTtls(Map<String, Long> cacheTtls) {
        this.cacheTtls = cacheTtls;
    }

    public static class ClusterProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class SslProperties {
        private boolean enabled = true;
        private boolean useSni = true;
        private String sniServerName;
        private boolean disablePeerVerification = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isUseSni() {
            return useSni;
        }

        public void setUseSni(boolean useSni) {
            this.useSni = useSni;
        }

        public String getSniServerName() {
            return sniServerName;
        }

        public void setSniServerName(String sniServerName) {
            this.sniServerName = sniServerName;
        }

        public boolean isDisablePeerVerification() {
            return disablePeerVerification;
        }

        public void setDisablePeerVerification(boolean disablePeerVerification) {
            this.disablePeerVerification = disablePeerVerification;
        }
    }

    public static class SocketProperties {
        private boolean keepAlive = true;
        private boolean tcpNoDelay = true;
        private Duration connectTimeout = Duration.ofMillis(5000);

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }

        public boolean isTcpNoDelay() {
            return tcpNoDelay;
        }

        public void setTcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }
    }

    public static class TopologyRefreshProperties {
        private boolean adaptiveRefreshEnabled = true;
        private Duration periodicRefreshInterval = Duration.ofSeconds(30);
        private boolean dynamicRefreshSources = true;

        public boolean isAdaptiveRefreshEnabled() {
            return adaptiveRefreshEnabled;
        }

        public void setAdaptiveRefreshEnabled(boolean adaptiveRefreshEnabled) {
            this.adaptiveRefreshEnabled = adaptiveRefreshEnabled;
        }

        public Duration getPeriodicRefreshInterval() {
            return periodicRefreshInterval;
        }

        public void setPeriodicRefreshInterval(Duration periodicRefreshInterval) {
            this.periodicRefreshInterval = periodicRefreshInterval;
        }

        public boolean isDynamicRefreshSources() {
            return dynamicRefreshSources;
        }

        public void setDynamicRefreshSources(boolean dynamicRefreshSources) {
            this.dynamicRefreshSources = dynamicRefreshSources;
        }
    }
}
