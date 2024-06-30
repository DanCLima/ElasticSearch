package com.elasticsearch.search.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.HighlighterType;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.elasticsearch.search.api.model.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.altindag.ssl.SSLFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import javax.swing.text.Highlighter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

@Component
public class EsClient {
    private BoolQuery.Builder queryBuilder;
    private SearchRequest.Builder searchBuilder;
    private ElasticsearchClient elasticsearchClient;
    private static final Integer PAGE_SIZE = 10;

    public static String extractBetweenQuotes(String input) {
        String regex = "\"(.*?)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void setQuery (String query) {
        Query multiMatchQuery, matchPhraseQuery;

        // Extrai da string query o conteúdo que estiver entre aspas (se houver)
        String betweenQuotes = extractBetweenQuotes(query);

        // Se não houver conteúdo entre aspas, realiza uma busca padrão
        if (betweenQuotes == null) {
            multiMatchQuery = MultiMatchQuery.of(m -> m.fields("content", "title").query(query))._toQuery();
            matchPhraseQuery = MatchPhraseQuery.of(q -> q.field("content").query(query))._toQuery();

            // O must exige uma busca comum e o should aumenta a relevância de correspondências exatas com o match phrase
            queryBuilder = queryBuilder.must(multiMatchQuery).should(matchPhraseQuery);

            // Se houver conteúdo entre aspas, realiza uma busca personalizada
        } else {
            matchPhraseQuery = MatchPhraseQuery.of(q -> q.field("content").query(betweenQuotes))._toQuery();
            multiMatchQuery = MultiMatchQuery.of(m -> m.fields("content", "title").query(query))._toQuery();

            // O must exige correspondência exata com o match pgrase, enquanto o should inclui a busca padrão para o restante do conteúdo
            queryBuilder = queryBuilder.must(matchPhraseQuery).should(multiMatchQuery);
        }
    }

    private void setFilter(Filter filter) {
        Query filterQuery;
        String field = filter.getField().getValue();
        String value = filter.getValue();

        if (filter.getOrder().getValue().equals("gte")) {
            filterQuery = RangeQuery.of(r -> r
                    .field(field)
                    .gte(JsonData.of(value))
            )._toQuery();
        } else {
            filterQuery = RangeQuery.of(r -> r
                    .field(field)
                    .lte(JsonData.of(value))
            )._toQuery();
        }

        queryBuilder = queryBuilder.filter(filterQuery);
    }

    private void setSort(Sort sort) {
        SortOptions sortOptions;
        SortOrder sortOrder;

        String field = sort.getField().getValue();
        String order = sort.getOrder().getValue();

        if (order.equals("asc")) {
            sortOrder = SortOrder.Asc;
        } else {
            sortOrder = SortOrder.Desc;
        }

        System.out.println("SortField: " + field + " SortOrder: " + sortOrder);

        sortOptions = SortOptions.of(s -> s
                .field(FieldSort.of(f -> f
                        .field(field)
                        .order(sortOrder)
                ))
        );

        searchBuilder = searchBuilder.sort(sortOptions);
    }

    public EsClient() {
        createConnection();
    }

    private void createConnection() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        String USER = "elastic";
        String PWD = "user123";
        credentialsProvider.setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(USER, PWD));

        SSLFactory sslFactory = SSLFactory.builder()
            .withUnsafeTrustMaterial()
            .withUnsafeHostnameVerifier()
            .build();

        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "https"))
            .setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) -> httpClientBuilder
                .setDefaultCredentialsProvider(credentialsProvider)
                .setSSLContext(sslFactory.getSslContext())
                .setSSLHostnameVerifier(sslFactory.getHostnameVerifier())
            ).build();

        ElasticsearchTransport transport = new RestClientTransport(
            restClient,
            new JacksonJsonpMapper()
        );

        elasticsearchClient = new co.elastic.clients.elasticsearch.ElasticsearchClient(transport);
    }

    public SearchResponse search(String query, Integer page, Filter filter, Sort sort) {
        this.queryBuilder = new BoolQuery.Builder();
        this.searchBuilder = new SearchRequest.Builder();

        setQuery(query);
        if (filter != null) {
            setFilter(filter);
            System.out.println("ENTROU NO FILTRO");
        }

        if (sort != null) {
            setSort(sort);
            System.out.println("ENTROU NO SORT");
        }

        Map<String, HighlightField> map = new HashMap<>();
        map.put("content", HighlightField.of(h -> h
                .preTags("<strong>")
                .postTags("</strong>")
                .numberOfFragments(1)
                .fragmentSize(400)
        ));

        Highlight highlight = Highlight.of(h -> h
                .type(HighlighterType.Unified)
                .fields(map)
        );

        SearchResponse<ObjectNode> response;

        searchBuilder = searchBuilder
                .index("wikipedia")
                .from((page - 1) * PAGE_SIZE)
                .size(PAGE_SIZE)
                .query(queryBuilder.build()._toQuery());

        try {
            response = elasticsearchClient.search(searchBuilder.highlight(highlight).build(), ObjectNode.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return response;
    }
}
