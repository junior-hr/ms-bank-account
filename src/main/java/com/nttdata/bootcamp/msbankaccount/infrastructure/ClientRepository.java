package com.nttdata.bootcamp.msbankaccount.infrastructure;

import com.nttdata.bootcamp.msbankaccount.config.WebClientConfig;
import com.nttdata.bootcamp.msbankaccount.model.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class ClientRepository {

    @Value("${local.property.host.ms-client}")
    private String propertyHostMsClient;
    
    @Autowired
    ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    public Mono<Client> findClientByDni(String documentNumber) {
        log.info("IniFind--findClientByDni------- documentNumber: " + documentNumber);
        WebClientConfig webconfig = new WebClientConfig();
        return webconfig.setUriData("http://" + propertyHostMsClient + ":8080")
                .flatMap(d -> webconfig.getWebclient().get().uri("/api/clients/documentNumber/" + documentNumber).retrieve()
                        .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new Exception("Error 400")))
                        .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new Exception("Error 500")))
                        .bodyToMono(Client.class)
                        .transform(it -> reactiveCircuitBreakerFactory.create("parameter-service").run(it, throwable -> Mono.just(new Client())))
                );
    }

    public Mono<Void> updateProfileClient(String documentNumber, String profile) {
        log.info("--updateProfileClient------- documentNumber: " + documentNumber);
        log.info("--updateProfileClient------- profile: " + profile);
        WebClientConfig webconfig = new WebClientConfig();
        return webconfig.setUriData("http://" + propertyHostMsClient + ":8080")
                .flatMap(d -> webconfig.getWebclient().put()
                        .uri("/api/clients/documentNumber/" + documentNumber + "/profile/" + profile)
                        .accept(MediaType.APPLICATION_JSON).retrieve()
                        .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new Exception("Error 400")))
                        .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new Exception("Error 500")))
                        .bodyToMono(Void.class)
                        .transform(it -> reactiveCircuitBreakerFactory.create("parameter-service").run(it, throwable -> Mono.empty()))
                );
    }

}
