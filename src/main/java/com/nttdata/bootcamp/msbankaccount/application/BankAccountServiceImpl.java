package com.nttdata.bootcamp.msbankaccount.application;

import com.nttdata.bootcamp.msbankaccount.config.WebClientConfig;
import com.nttdata.bootcamp.msbankaccount.dto.BankAccountDto;
import com.nttdata.bootcamp.msbankaccount.exception.ResourceNotFoundException;
import com.nttdata.bootcamp.msbankaccount.model.Client;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;
import com.nttdata.bootcamp.msbankaccount.model.BankAccount;
import org.springframework.beans.factory.annotation.Autowired;
import com.nttdata.bootcamp.msbankaccount.infrastructure.BankAccountRepository;

@Service
@Slf4j
public class BankAccountServiceImpl implements BankAccountService {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    public Mono<Client> findClientByDni(String documentNumber) {
        WebClientConfig webconfig = new WebClientConfig();
        return webconfig.setUriData("http://localhost:8080/").flatMap(
                d -> {
                    return webconfig.getWebclient().get().uri("/api/clients/documentNumber/" + documentNumber).retrieve().bodyToMono(Client.class);
                }
        );
    }

    @Override
    public Flux<BankAccount> findAll() {
        return bankAccountRepository.findAll();
    }

    @Override
    public Mono<BankAccount> findById(String idBankAccount) {
        return Mono.just(idBankAccount)
                .flatMap(bankAccountRepository::findById)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cuenta Bancaria", "idBankAccount", idBankAccount)));
    }


    @Override
    public Mono<BankAccount> save(BankAccountDto bankAccountDto) {

        return findClientByDni(bankAccountDto.getDocumentNumber())
                .flatMap(clnt -> {
                    return validateNumberClientAccounts(clnt, bankAccountDto, "save").flatMap(o -> {
                        return bankAccountDto.validateFields()
                                .flatMap(at -> {
                                    if (at.equals(true)) {
                                        return bankAccountDto.MapperToBankAccount(clnt)
                                                .flatMap(ba -> {
                                                    log.info("sg MapperToBankAccount-------: ");
                                                    //return Mono.just(ba);
                                                    return bankAccountRepository.save(ba);
                                                });
                                    } else {
                                        return Mono.error(new ResourceNotFoundException("Tipo Cuenta", "AccountType", bankAccountDto.getAccountType()));
                                    }
                                });
                    });
                });

    }

    public Mono<Boolean> validateNumberClientAccounts(Client client, BankAccountDto bankAccountDto, String method) {
        log.info("ini validateNumberClientAccounts-------: ");
        Boolean isOk = false;
        if (client.getClientType().equals("Personal") ) {
            if(method.equals("save")){
                Flux<BankAccount> lista = bankAccountRepository.findByAccountClient(client.getDocumentNumber(), bankAccountDto.getAccountType());
                return lista.count().flatMap(cnt -> {
                    log.info("1 Personal cnt-------: ", cnt);
                    if (cnt >= 1) {
                        log.info("2 Personal cnt-------: ", cnt);
                        return Mono.error(new ResourceNotFoundException("Tipo Cliente", "ClientType", client.getClientType()));
                    } else {
                        log.info("3 Personal cnt-------: ", cnt);
                        return Mono.just(true);
                    }
                });
            }else{
                return Mono.just(true);
            }
        } else if (client.getClientType().equals("Business")) {
            if (bankAccountDto.getAccountType().equals("Checking-account")) {
                log.info("1 Business Checking-account-------: ");
                if (bankAccountDto.getListHeadline() == null) {
                    return Mono.error(new ResourceNotFoundException("Titular", "ListHeadline", ""));
                } else {
                    log.info("1 Business -------: ");
                    return Mono.just(true);
                }
            } else {
                return Mono.error(new ResourceNotFoundException("Tipo Cliente", "ClientType", client.getClientType()));
            }
        } else {
            return Mono.error(new ResourceNotFoundException("Tipo Cliente", "ClientType", client.getClientType()));
        }
    }

    @Override
    public Mono<BankAccount> update(BankAccountDto bankAccountDto, String idBankAccount) {

        return findClientByDni(bankAccountDto.getDocumentNumber())
                .flatMap(clnt -> {
                    return validateNumberClientAccounts(clnt, bankAccountDto, "update").flatMap(o -> {
                        return bankAccountDto.validateFields()
                                .flatMap(at -> {
                                    if (at.equals(true)) {
                                        return bankAccountRepository.findById(idBankAccount)
                                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cuenta Bancaria", "idBankAccount", idBankAccount)))
                                                .flatMap(x -> {
                                                    x.setClient(clnt);
                                                    x.setAccountType(bankAccountDto.getAccountType());
                                                    x.setCardNumber(bankAccountDto.getCardNumber());
                                                    x.setAccountNumber(bankAccountDto.getAccountNumber());
                                                    x.setCommission(bankAccountDto.getCommission());
                                                    x.setMovementDate(bankAccountDto.getMovementDate());
                                                    x.setMaximumMovement(bankAccountDto.getMaximumMovement());
                                                    x.setListHeadline(bankAccountDto.getListHeadline());
                                                    x.setListAuthorizedSignatories(bankAccountDto.getListAuthorizedSignatories());
                                                    x.setStartingAmount(bankAccountDto.getStartingAmount());
                                                    x.setCurrency(bankAccountDto.getCurrency());
                                                    return bankAccountRepository.save(x);
                                                });
                                    } else {
                                        return Mono.error(new ResourceNotFoundException("Tipo Cuenta", "AccountType", bankAccountDto.getAccountType()));
                                    }
                                });
                    });
                });

    }

    @Override
    public Mono<Void> delete(BankAccount bankAccount) {
        return bankAccountRepository.delete(bankAccount);
    }

}
