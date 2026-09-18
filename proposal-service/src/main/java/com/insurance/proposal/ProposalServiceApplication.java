package com.insurance.proposal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * The formal application for insurance. A proposal is created from an ACCEPTED quote, completed by the
 * customer (proposer, nominee, declarations), submitted, and reviewed by an AGENT/ADMIN. An APPROVED
 * proposal is what payment-service charges and policy-service issues from.
 *
 * <p>No cache: a proposal is read a handful of times by one customer and one reviewer; the cost of
 * eviction logic outweighs any benefit. Every state change is recorded in proposal_status_history.
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@ConfigurationPropertiesScan
public class ProposalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProposalServiceApplication.class, args);
    }
}
