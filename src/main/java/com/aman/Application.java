/*
 * Copyright 2024 Oracle and/or its affiliates
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aman;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.Micronaut;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.concurrent.ExecutorService;


public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}

@Controller
class HelloController{

    private final Logger log = LoggerFactory.getLogger(HelloController.class);
    @Inject
    EchoService service;

    @Inject
    FactorialService factorialService;

    @Get("/factorial/{number}")
    public Mono<?> factorial(@PathVariable("number") Long number, @Nullable @QueryValue("externalApi") Boolean externalApi){
        String uuid = UUID.randomUUID().toString();
        log.info("[uuid - {}] Controller - Factorial of a number {} ", uuid, number);
        Mono m = Mono.create(monoSink -> factorialService.factorialOfANumber(uuid, number, externalApi, monoSink));
        return  m;
    }

    @Get("/echo")
    public Mono<?> echo(String message){
        return Mono.create(r -> {
                        service.echo(r, message);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Get("/hello")
    public Mono<?> hello(String message) throws InterruptedException {
        Thread.sleep(15000);
        return Mono.just("Hello "+message);
    }
}

@Singleton
class FactorialService{

    private final ExecutorService boundedElasticExecutorService;

    private final Logger log = LoggerFactory.getLogger(FactorialService.class);

    @Value("${sleep.enabled}")
    Boolean sleepEnabled;

    @Inject
    HelloClient client;

    FactorialService(@Named("custom-bounded-elastic") ExecutorService boundedElasticExecutorService) {
        this.boundedElasticExecutorService = boundedElasticExecutorService;
    }

    public Long factorial(Long n){
        if (n == 0 || n == 1) {
            return 1L;
        } else {
            return n * factorial(n - 1);
        }
    }

    @Executable
    public void factorialOfANumber(String uuid,Long n, Boolean extApi, MonoSink result){
        log.info("[uuid - {}]Service - Getting the factorial of {}",uuid, n);
        long f = factorial(n);
        if(extApi==null || !extApi){
            if (sleepEnabled!=null && sleepEnabled){
                long t = (long) Math.floor(Math.random() * (2000 - 500 + 1) + 500);
                try {
                    Thread.sleep(t);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            result.success(f);
            return;
        }
        log.info("[uuid - {}]Service - Getting the factorial of {} externally", uuid, n);
        client.factorial(n, false, uuid).doOnSuccess(r->{
            log.info("[uuid - {}] On Success ", uuid);
            result.success(f+"-"+r);
                })
                .doOnError(t->{
                    log.info("[uuid - {}] On Error ", uuid);
                    result.error(t);
                }).subscribe();
    }
}
class EchoService{
    @Inject
    HelloClient client;


    public void echo(MonoSink res, String message) {
//        httpClient.exchange(HttpRequest.GET(""))
        client.hello(message).doOnSuccess(res::success)
                .doOnError(throwable -> res.error(throwable))
                .subscribe();
    }
}

@Client(id="http://localhost:8081")
interface HelloClient{

    @Get("/hello")
    Mono<String> hello(@QueryValue("message") String message);

    @Get("/factorial/{number}")
    Mono<Long> factorial(@PathVariable("number") Long number, @Nullable @QueryValue("externalApi") Boolean externalApi, @Nullable @QueryValue("uuid") String uuid);
}