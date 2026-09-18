package com.insurance.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service registry for the platform.
 *
 * <p>Every business service registers itself here on startup and sends heartbeats.
 * The API Gateway and OpenFeign clients resolve logical service names (for example
 * {@code lb://quote-service}) to live host:port instances by reading this registry.
 *
 * <p>Interview notes:
 * <ul>
 *   <li><b>Problem solved:</b> in a dynamic environment (containers, autoscaling) IPs change
 *       constantly; hard-coding URLs does not work. Eureka is a client-side discovery registry.</li>
 *   <li><b>Internals:</b> clients POST a registration, renew it every 30s (configurable) and the
 *       server evicts instances whose lease expires. Clients cache the registry locally and refresh it
 *       periodically, so a short Eureka outage does not break traffic that is already flowing.</li>
 *   <li><b>Alternatives:</b> Consul, ZooKeeper, Kubernetes DNS/Service objects, cloud load balancers.</li>
 *   <li><b>Scaling:</b> run 2+ Eureka peers that replicate to each other; clients list all peers.</li>
 * </ul>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
