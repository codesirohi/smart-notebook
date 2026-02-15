package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.QueryRequest;
import org.sirohi.smartnotebook.dto.QueryResponse;
import org.sirohi.smartnotebook.service.QueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.query(
                request.question(),
                request.topK().orElse(5),
                request.documentIds().orElse(List.of()));
        return ResponseEntity.ok(response);
    }
}
