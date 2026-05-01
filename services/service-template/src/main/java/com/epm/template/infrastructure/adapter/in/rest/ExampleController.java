package com.epm.template.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.in.CreateExampleUseCase;
import com.epm.template.domain.port.in.FindExampleUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examples")
class ExampleController {

    private final CreateExampleUseCase createUseCase;
    private final FindExampleUseCase findUseCase;

    ExampleController(CreateExampleUseCase createUseCase, FindExampleUseCase findUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
    }

    @PostMapping
    ResponseEntity<ExampleResponse> create(@RequestBody CreateExampleRequest request) {
        Example example = createUseCase.create(request.name());
        return ResponseEntity.ok(ExampleResponse.from(example));
    }

    @GetMapping("/{id}")
    ResponseEntity<ExampleResponse> findById(@PathVariable UUID id) {
        return findUseCase
                .findById(id)
                .map(ExampleResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
