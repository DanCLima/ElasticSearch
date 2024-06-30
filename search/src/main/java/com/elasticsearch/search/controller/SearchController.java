package com.elasticsearch.search.controller;

import com.elasticsearch.search.api.facade.SearchApi;
import com.elasticsearch.search.api.model.*;
import com.elasticsearch.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@CrossOrigin
@RestController
public class SearchController implements SearchApi {

    private SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public CompletableFuture<ResponseEntity<Result>> search(String query, Integer page, String filterField, String filterValue, String filterOrder, String sortMode, String sortField) {
        Filter filter = null;
        Sort sort = null;

        if(filterField != null) {
            filter = new Filter();
            filter.setValue(filterValue);
            filter.setField(Filter.FieldEnum.fromValue(filterField));
            filter.setOrder(Filter.OrderEnum.fromValue(filterOrder));
        }

        if(sortField != null) {
            sort = new Sort();
            sort.setField(Sort.FieldEnum.fromValue(sortField));
            sort.setOrder(Sort.OrderEnum.fromValue(sortMode));
        }

        var result = searchService.submitQuery(query, page, filter, sort);
        return CompletableFuture.supplyAsync(() -> ResponseEntity.ok(result));
    }
}
