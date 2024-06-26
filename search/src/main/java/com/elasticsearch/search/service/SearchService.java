package com.elasticsearch.search.service;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.elasticsearch.search.api.model.Result;
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

    public List<Result> submitQuery(String query, Integer page) {
//        Integer page;

        if (isNull(page) || page <= 0) {
            page = 1;
        }

        var searchResponse = esClient.search(query, page);
        List<Hit<ObjectNode>> hits = searchResponse.hits().hits();

        var resultsList = hits.stream().map(h ->
                new Result()
                        .abs(treatContent(h.source().get("content").asText()))
                        .title(h.source().get("title").asText())
                        .url(h.source().get("url").asText())
                        .readingTime(h.source().get("reading_time").asInt())
                        .dateCreation(h.source().get("dt_creation").asText())
        ).collect(Collectors.toList());

        long totalHits = searchResponse.hits().total().value();
        System.out.println("Total de hits: " + totalHits);

        return resultsList;
    }

    private String treatContent(String content) {
        content = content.replaceAll("</?(som|math)\\d*>", "");
        content = content.replaceAll("[^A-Za-z\\s]+", "");
        content = content.replaceAll("\\s+", " ");
        content = content.replaceAll("^\\s+", "");
        return content;
    }
}
