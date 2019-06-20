package com.melardev.cloud.proxy.controllers;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;

@RestController
@RequestMapping(value = "/todos", produces = MediaType.APPLICATION_JSON_VALUE)
public class TodosProxyController {

    private final RestTemplate restTemplate;

    @Autowired
    private Tracing tracing;

    @Autowired
    private Tracer tracer;

    @Autowired
    ObjectMapper objectMapper;

    public TodosProxyController() {
        this.restTemplate = new RestTemplate();
    }

    @Autowired
    private LoadBalancerClient loadBalancer;

    public String getTodoBaseMicroServiceUrl() {
        ServiceInstance serviceInstance = loadBalancer.choose("todo-service");
        String url = serviceInstance.getUri().toString();
        if (!url.endsWith("/"))
            url += "/";
        return url;
    }

    private static HttpEntity<String> getHeadersRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<String> getWriteRequestEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        return new HttpEntity<>(requestBody, headers);
    }

    private ResponseEntity<String> fetch(String url) {
        return fetch(url, getHeadersRequestEntity());
    }

    private ResponseEntity<String> fetch(String url, HttpEntity requestEntity) {
        return fetch(url, HttpMethod.GET, requestEntity);
    }

    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod) {
        return fetch(url, httpMethod, getHeadersRequestEntity());
    }


    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod, HttpEntity requestEntity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url,
                    httpMethod, requestEntity, String.class);
            return response;
        } catch (RestClientException ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse", commandProperties = {
            @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "20"),
            @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"), // default already 50
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "1000") // default already 1000
    })
    @GetMapping
    public ResponseEntity<String> index(HttpServletRequest request) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos");
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/{id}")
    public ResponseEntity<String> get(HttpServletRequest request, @PathVariable("id") Long id) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos/" + id);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/pending")
    public ResponseEntity<String> getNotCompletedTodos(HttpServletRequest request) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos/pending");
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/completed")
    public ResponseEntity<String> getCompletedTodos(HttpServletRequest request) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos/completed");
    }


    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @PostMapping
    public ResponseEntity<String> create(HttpServletRequest request, @RequestBody String todo) {
        String url = getTodoBaseMicroServiceUrl() + "/api/todos";
        return fetch(url, HttpMethod.POST, getWriteRequestEntity(todo));
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @PutMapping("/{id}")
    public ResponseEntity update(HttpServletRequest request, @PathVariable("id") Long id,
                                 @RequestBody String todo) {
        String url = getTodoBaseMicroServiceUrl() + "/api/todos/" + id;
        return fetch(url, HttpMethod.PUT, getWriteRequestEntity(todo));
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String url = getTodoBaseMicroServiceUrl() + "/api/todos/" + id;
        return fetch(url, HttpMethod.DELETE);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @DeleteMapping
    public ResponseEntity<String> deleteAll(HttpServletRequest request) {
        String url = getTodoBaseMicroServiceUrl() + "/api/todos/";
        return fetch(url, HttpMethod.DELETE);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping(value = "/after/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateAfter(HttpServletRequest request, @PathVariable("date") String date) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos/after/" + date);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping(value = "/before/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateBefore(HttpServletRequest request, @PathVariable("date") String date) {
        return fetch(getTodoBaseMicroServiceUrl() + "/api/todos/after/" + date);
    }

    public ResponseEntity<String> fallbackResponse(HttpServletRequest request) {

        TraceContext.Extractor<HttpServletRequest> extractor = tracing.propagation()
                .extractor(HttpServletRequest::getHeader);

        Span span = tracer.nextSpan(extractor.extract(request));

        System.out.printf("Span Message{ traceId: %s, spanId: %s}%n", span.context().traceIdString(), span.context().spanIdString());

        HashMap<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("full_messages", Collections.singletonList("Server is down"));

        String responseStr;
        try {
            responseStr = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            responseStr = "{success: false}";
        }

        return new ResponseEntity<>(responseStr, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
