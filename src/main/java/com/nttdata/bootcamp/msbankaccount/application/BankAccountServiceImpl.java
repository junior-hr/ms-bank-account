package com.nttdata.bootcamp.msbankaccount.application;

import com.nttdata.bootcamp.msbankaccount.dto.BankAccountDto;
import com.nttdata.bootcamp.msbankaccount.dto.DebitCardDto;
import com.nttdata.bootcamp.msbankaccount.dto.bean.BankAccountBean;
import com.nttdata.bootcamp.msbankaccount.exception.ResourceNotFoundException;
import com.nttdata.bootcamp.msbankaccount.infrastructure.*;
import com.nttdata.bootcamp.msbankaccount.model.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class BankAccountServiceImpl implements BankAccountService {

    @Autowired
    private BankAccountRepository bankAccountRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private MovementRepository movementRepository;
    @Autowired
    private CreditRespoditory creditRespoditory;

    @Autowired
    private DebitCardRepository debitCardRepository;

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
    public Mono<BankAccount> findByAccountNumber(String accountNumber) {
        return Mono.just(accountNumber)
                .flatMap(bankAccountRepository::findByAccountNumber)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Número Cuenta Bancaria", "accountNumber", accountNumber)));
    }

    @Override
    public Flux<BankAccount> findByDocumentNumber(String documentNumber, String accountType) {
        log.info("ini----findByDocumentNumber-------: ");
        log.info("ini----findByDocumentNumber-------documentNumber, accountType : " + documentNumber + " --- " + accountType);
        return bankAccountRepository.findByAccountClient(documentNumber, accountType)
                .flatMap(d -> {
                    log.info("ini----findByAccountClient-------documentNumber: " + documentNumber);
                    log.info("ini----findByAccountClient-------d.getAccountNumber(): " + d.getAccountNumber());
                    return movementRepository.findLastMovementByAccountNumber(d.getAccountNumber())
                            .switchIfEmpty(Mono.defer(() -> {
                                log.info("----2 switchIfEmpty-------: ");
                                Movement mv = Movement.builder()
                                        .balance(d.getStartingAmount())
                                        .build();
                                return Mono.just(mv);
                            }))
                            .flatMap(m -> {
                                log.info("----findByDocumentNumber setBalance-------: ");
                                d.setBalance(m.getBalance());
                                return Mono.just(d);
                            });
                });
    }

    @Override
    public Mono<BankAccountDto> findMovementsByDocumentNumber(String documentNumber, String accountNumber) {
        log.info("ini----findByDocumentNumber-------: ");
        log.info("ini----findByDocumentNumber-------documentNumber, accountNumber : " + documentNumber + " --- " + accountNumber);
        return bankAccountRepository.findByAccountAndDocumentNumber(documentNumber, accountNumber)
                .flatMap(d -> {
                    log.info("ini----findByAccountClient-------: ");
                    return movementRepository.findMovementsByAccountNumber(accountNumber)
                            .collectList()
                            .flatMap(m -> {
                                log.info("----findByDocumentNumber setBalance-------: ");
                                d.setMovements(m);
                                return Mono.just(d);
                            });
                });
    }

    @Override
    public Mono<BankAccount> save(BankAccountDto bankAccountDto) {
        return Mono.just(bankAccountDto)
                .flatMap(badto -> {
                    if (badto.getCardNumber() != null) {
                        return debitCardRepository.findByCardNumber(badto.getCardNumber())
                                .flatMap(dc -> {
                                    log.info("--doOnNext-------findByCardNumber 1" + badto.getCardNumber());
                                    log.info("--doOnNext-------findByCardNumber 2" + badto.toString());
                                    log.info("--doOnNext-------findByCardNumber dc" + dc.toString());
                                    log.info("--doOnNext-------findByCardNumber dc getIdDebitCard" + dc.getIdDebitCard());
                                    if (dc.getIdDebitCard() != null) {
                                        log.info("--getIdDebitCard-------if ");
                                        return bankAccountRepository.findByClientAndCard(badto.getDocumentNumber(), badto.getCardNumber())
                                                .collectList()
                                                .flatMap(dcc -> {
                                                    log.info("--flatMap-------findByClientAndCard ");
                                                    log.info("--flatMap-------findByClientAndCard " + dcc.toString());
                                                    DebitCardDto debitCardDto = DebitCardDto.builder()
                                                            .idDebitCard(dc.getIdDebitCard())
                                                            .cardNumber(dc.getCardNumber())
                                                            .build();

                                                    Stream<BankAccount> BankAccounts = dcc.stream();
                                                    Long countBA = dcc.stream().count();
                                                    log.info("1--flatMap-------BankAccounts.count() > 0 - " + countBA);
                                                    if (countBA > 0) {
                                                        log.info("2--flatMap-------BankAccounts.count() > 0 - " + countBA);
                                                        Optional<Integer> finalOrder = BankAccounts
                                                                .map(mp -> mp.getDebitCard() != null ? mp.getDebitCard().getOrder() : 0)
                                                                .max(Comparator.naturalOrder());
                                                        log.info("3--flatMap-------finalOrder - " + finalOrder.toString());

                                                        if (finalOrder.isPresent()) {
                                                            log.info("--isPresent-------finalOrder.get(): " + finalOrder.get());
                                                            debitCardDto.setIsMainAccount(false);
                                                            debitCardDto.setOrder((finalOrder.get() + 1));
                                                            badto.setDebitCard(debitCardDto);
                                                            return Mono.just(badto);
                                                        } else {
                                                            log.info("--noPresent-------finalOrder ");
                                                            return Mono.just(badto);
                                                        }
                                                    } else {
                                                        log.info("--tarjeta nueva------- ");
                                                        debitCardDto.setIsMainAccount(true);
                                                        debitCardDto.setOrder(1);
                                                        badto.setDebitCard(debitCardDto);
                                                        return Mono.just(badto);
                                                    }
                                                });
                                    } else {
                                        log.info("--getIdDebitCard-------else ");
                                        return Mono.just(badto);
                                    }
                                })
                                .then(Mono.just(badto));
                    } else {
                        return Mono.just(badto);
                    }
                })
                .flatMap(badto -> {

                    if (badto.getAccountType().equals("Savings-account")) { // cuenta de ahorros.
                        return badto.MapperToSavingAccount();
                    } else if (badto.getAccountType().equals("FixedTerm-account")) { // cuenta plazos fijos.
                        return badto.MapperToFixedTermAccount();
                    } else if (badto.getAccountType().equals("Checking-account")) { // current account.
                        return badto.MapperToCheckingAccount();
                    } else {
                        return Mono.error(new ResourceNotFoundException("Tipo Cuenta", "AccountType", badto.getAccountType()));
                    }
                })
                .flatMap(mba -> {
                    log.info("sg MapperToAccount-------mba: " + mba.toString());
                    log.info("sg MapperToAccount-------mba.getDocumentNumber: " + (mba.getDocumentNumber() != null ? mba.getDocumentNumber() : ""));
                    return clientRepository.findClientByDni(mba.getDocumentNumber())
                            .doOnNext(clnt -> log.info("sg findClientByDni-------clnt: " + clnt.toString()))
                            .flatMap(clnt -> validateNumberClientAccounts(clnt, mba, "save").then(Mono.just(clnt)))
                            .flatMap(clnt -> mba.validateFields()
                                    .flatMap(at -> mba.MapperToBankAccount(clnt))
                                    .doOnNext(at -> log.info("sg MapperToBankAccount-------: "))
                                    .flatMap(ba -> verifyThatYouHaveACreditCard(clnt, mba.getMinimumAmount())
                                            .doOnNext(o -> log.info("sg--verifyThatYouHaveACreditCard-------o: " + o.toString()))
                                            .flatMap(o -> {
                                                ba.setBalance(ba.getStartingAmount());
                                                if (o.equals(true) && clnt.getClientType().equals("Business") && mba.getAccountType().equals("Checking-account")) {
                                                    ba.setCommission(0.0);
                                                }
                                                return Mono.just(ba);
                                            })
                                            .flatMap(bankAccountRepository::save)
                                    )

                            );
                });
    }

    @Override
    public Mono<BankAccount> update(BankAccountDto bankAccountDto, String idBankAccount) {
        return Mono.just(bankAccountDto)
                .flatMap(badto -> {
                    if (badto.getAccountType().equals("Savings-account")) { // cuenta de ahorros.
                        return badto.MapperToSavingAccount();
                    } else if (badto.getAccountType().equals("FixedTerm-account")) { // cuenta plazos fijos.
                        return badto.MapperToFixedTermAccount();
                    } else if (badto.getAccountType().equals("Checking-account")) { // current account.
                        return badto.MapperToCheckingAccount();
                    } else {
                        return Mono.error(new ResourceNotFoundException("Tipo Cuenta", "AccountType", badto.getAccountType()));
                    }
                }).flatMap(mba -> {
                    log.info("sg MapperToAccount-------mba: " + mba.toString());
                    log.info("sg MapperToAccount-------mba.getDocumentNumber: " + (mba.getDocumentNumber() != null ? mba.getDocumentNumber() : ""));
                    return clientRepository.findClientByDni(mba.getDocumentNumber())
                            .flatMap(clnt -> {
                                log.info("sg findClientByDni-------clnt: " + clnt.toString());
                                return validateNumberClientAccounts(clnt, mba, "update").then(Mono.just(clnt));
                            })
                            .flatMap(clnt -> mba.validateFields()
                                    .flatMap(at -> mba.MapperToBankAccount(clnt))
                                    .flatMap(ba -> {
                                        log.info("sg MapperToBankAccount-------: ");
                                        return verifyThatYouHaveACreditCard(clnt, mba.getMinimumAmount())
                                                .flatMap(o -> {
                                                    log.info("sg--verifyThatYouHaveACreditCard-------o: " + o.toString());
                                                    if (o.equals(true) && clnt.getClientType().equals("Business") && mba.getAccountType().equals("Checking-account")) {
                                                        ba.setCommission(0.0);
                                                    }
                                                    return bankAccountRepository.findById(idBankAccount)
                                                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cuenta Bancaria", "idBankAccount", idBankAccount)))
                                                            .flatMap(x -> {
                                                                ba.setIdBankAccount(x.getIdBankAccount());
                                                                return bankAccountRepository.save(ba);
                                                            });
                                                });
                                    })

                            );
                });
    }

    @Override
    public Mono<Void> delete(BankAccount bankAccount) {
        return bankAccountRepository.delete(bankAccount);
    }

    public Mono<Boolean> verifyThatYouHaveACreditCard(Client client, Double minimumAmount) {
        log.info("ini verifyThatYouHaveACreditCard-------minimumAmount: " + (minimumAmount == null ? "" : minimumAmount.toString()));
        return creditRespoditory.findCreditsByDocumentNumber(client.getDocumentNumber()).count()
                .flatMap(cnt -> {
                    log.info("--verifyThatYouHaveACreditCard------- cnt: " + cnt);
                    String profile = "0";
                    if (cnt > 0) {
                        if (client.getClientType().equals("Personal")) {
                            if (minimumAmount == null) {
                                log.info("1--verifyThatYouHaveACreditCard-------: ");
                                return clientRepository.updateProfileClient(client.getDocumentNumber(), profile).then(Mono.just(false));
                            } else {
                                log.info("2--verifyThatYouHaveACreditCard-------: ");
                                profile = "VIP";
                                return clientRepository.updateProfileClient(client.getDocumentNumber(), profile).then(Mono.just(true));
                            }
                        } else if (client.getClientType().equals("Business")) {
                            log.info("3--verifyThatYouHaveACreditCard-------: ");
                            profile = "PYME";
                            return clientRepository.updateProfileClient(client.getDocumentNumber(), profile).then(Mono.just(true));

                        } else {
                            log.info("4--verifyThatYouHaveACreditCard-------: ");
                            return clientRepository.updateProfileClient(client.getDocumentNumber(), profile).then(Mono.just(false));
                        }

                    } else {
                        log.info("5--verifyThatYouHaveACreditCard-------: ");
                        return clientRepository.updateProfileClient(client.getDocumentNumber(), profile).then(Mono.just(false));
                    }
                });
    }

    public Mono<Boolean> validateNumberClientAccounts(Client client, BankAccountBean bankAccountBean, String method) {
        log.info("ini validateNumberClientAccounts-------: ");
        log.info("ini validateNumberClientAccounts-------client: " + client.toString());
        log.info("ini validateNumberClientAccounts-------bankAccountBean: " + bankAccountBean.toString());
        log.info("ini validateNumberClientAccounts-------bankAccountBean.getAccountType: " + (bankAccountBean.getAccountType() != null ? bankAccountBean.getAccountType() : ""));
        if (client.getClientType().equals("Personal")) {
            if (method.equals("save")) {
                return bankAccountRepository.findByAccountClient(client.getDocumentNumber(), bankAccountBean.getAccountType())
                        .count().flatMap(cnt -> {
                            log.info("1 Personal cnt-------: ", cnt);
                            if (cnt >= 1) {
                                log.info("2 Personal cnt-------: ", cnt);
                                return Mono.error(new ResourceNotFoundException("Tipo Cliente", "ClientType", client.getClientType()));
                            } else {
                                log.info("3 Personal cnt-------: ", cnt);
                                return Mono.just(true);
                            }
                        });
            } else {
                return Mono.just(true);
            }
        } else if (client.getClientType().equals("Business")) {
            if (bankAccountBean.getAccountType().equals("Checking-account")) {
                log.info("1 Business Checking-account-------: ");
                if (bankAccountBean.getListHeadline() == null) {
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
    public Flux<BankAccount> findByDocumentNumberAndWithdrawalAmount(String documentNumber, String cardNumber, Double withdrawalAmount) {
        log.info("ini----findByDocumentNumberAndWithdrawalAmount------- : " + documentNumber + " --- " + cardNumber + " --- " + withdrawalAmount);
        return bankAccountRepository.findByClientAndCardAndIsNotMainAccount(documentNumber, cardNumber)
                .collectList()
                .flatMap(dcc -> {
                    Stream<BankAccount> bankAccounts = dcc.stream();
                    Long countBA = dcc.stream().count();
                    if (countBA > 0) {
                        AtomicReference<Double> missingOutflowAmount = new AtomicReference<>(withdrawalAmount);
                        List<BankAccount> bcAV = bankAccounts
                                .sorted((o1, o2) -> o1.getDebitCard().getOrder().compareTo(o2.getDebitCard().getOrder()))
                                .filter(ft -> {
                                    log.info("filter------- : " + ft.toString());
                                    Double valIni = missingOutflowAmount.get();
                                    if (valIni <= 0) {
                                        return false;
                                    } else {
                                        Double balance = ft.getBalance() != null ? ft.getBalance() : 0;
                                        missingOutflowAmount.set(missingOutflowAmount.get() - balance);
                                        return true;
                                    }
                                })
                                .collect(Collectors.toList());
                        return Mono.just(bcAV);
                    } else {
                        return Mono.just(dcc);
                    }
                })
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<BankAccount> updateBalanceById(String idBankAccount, Double balance) {
        return bankAccountRepository.findById(idBankAccount)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cuenta Bancaria", "idBankAccount", idBankAccount)))
                .flatMap(x -> {
                    x.setBalance(balance);
                    return bankAccountRepository.save(x);
                });
    }

    @Override
    public Mono<BankAccount> findByDebitCardNumberAndIsMainAccount(String debitCardNumber) {
        return bankAccountRepository.findByCardNumberAndIsMainAccount(debitCardNumber)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Tarjeta de débito", "debitCardNumber", debitCardNumber)));
    }

}
