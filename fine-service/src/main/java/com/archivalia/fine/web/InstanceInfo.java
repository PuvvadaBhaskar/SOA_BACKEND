package com.archivalia.fine.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.appinfo.EurekaInstanceConfig;

/**
 * Describes the running instance (service name, Eureka instance id, host, port).
 * Used by the X-Served-By header and the /.../instance endpoint to make load balancing visible.
 */
@Component
public class InstanceInfo {

    private final Environment environment;
    private final ObjectProvider<EurekaInstanceConfig> eurekaInstanceConfig;

    public InstanceInfo(Environment environment, ObjectProvider<EurekaInstanceConfig> eurekaInstanceConfig) {
        this.environment = environment;
        this.eurekaInstanceConfig = eurekaInstanceConfig;
    }

    public String serviceName() {
        return environment.getProperty("spring.application.name", "unknown-service");
    }

    /** Actual port of the embedded server (works with PORT=..., -Dserver.port=... and port 0). */
    public String port() {
        return environment.getProperty("local.server.port",
                environment.getProperty("server.port", "unknown"));
    }

    public String instanceId() {
        EurekaInstanceConfig config = eurekaInstanceConfig.getIfAvailable();
        return config != null ? config.getInstanceId() : serviceName() + ":" + port();
    }

    public String servedBy() {
        return serviceName() + ":" + port();
    }

    public Map<String, Object> describe() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", serviceName());
        info.put("instanceId", instanceId());
        info.put("host", hostName());
        info.put("port", port());
        return info;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
