package com.elasticsearch.search.service;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.elasticsearch.search.api.model.*;
import com.elasticsearch.search.api.model.ResultResults;
import com.elasticsearch.search.domain.EsClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
public class SearchService {

    private final EsClient esClient;

    public SearchService(EsClient esClient) {
        this.esClient = esClient;
    }

    public Result submitQuery(String query, Integer page, Filter filter, Sort sort) {
        if (isNull(page) || page <= 0) {
            page = 1;
        }

        var searchResponse = esClient.search(query, page, filter, sort);
        List<Hit<ObjectNode>> hits = searchResponse.hits().hits();

        var result = new Result();
        result.setTotalHits((int) searchResponse.hits().total().value());
        result.setResults(
                hits.stream().map(h -> {
                            return new ResultResults()
                                    .abs(treatContent(h.source().get("content").asText()))
                                    .title(h.source().get("title").asText())
                                    .url(h.source().get("url").asText())
                                    .readingTime(h.source().get("reading_time").asInt())
                                    .dateCreation(h.source().get("dt_creation").asText())
                                    .highlight(h.highlight().get("content").get(0));
                            }
                ).collect(Collectors.toList())
        );
        return result;
    }

    private String treatContent(String content) {
        content = content.replaceAll("</?(som|math)\\d*>", "");
        content = content.replaceAll("[^A-Za-z\\s]+", "");
        content = content.replaceAll("\\s+", " ");
        content = content.replaceAll("^\\s+", "");
        return content;
    }
}
