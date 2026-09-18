package com.insurance.gateway.support;

import com.insurance.gateway.filter.GatewayHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for a downstream microservice: returns the identity/correlation headers it received,
 * which lets integration tests assert exactly what the gateway forwards.
 */
@RestController
public class EchoController {

    @RequestMapping("/internal/echo")
    public Map<String, String> echo(@RequestHeader HttpHeaders headers) {
        Map<String, String> received = new LinkedHashMap<>();
        received.put(GatewayHeaders.USER_ID, headers.getFirst(GatewayHeaders.USER_ID));
        received.put(GatewayHeaders.USER_EMAIL, headers.getFirst(GatewayHeaders.USER_EMAIL));
        received.put(GatewayHeaders.USER_ROLES, headers.getFirst(GatewayHeaders.USER_ROLES));
        received.put(GatewayHeaders.CORRELATION_ID, headers.getFirst(GatewayHeaders.CORRELATION_ID));
        return received;
    }
}
