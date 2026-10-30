package com.tlavu.moodly.modules.search.indexing.repository;

import com.tlavu.moodly.modules.search.indexing.model.CdcResumeToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcResumeTokenRepository extends MongoRepository<CdcResumeToken, String> {
}
