package com.ruwei.es.mapper;

import com.ruwei.es.doc.PostDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 对es进行增删改查的
 */
public interface PostEsMapper extends ElasticsearchRepository<PostDoc, Long> {
}