package com.tlavu.moodly.modules.search.repository;

import com.tlavu.moodly.modules.search.model.mongodb.CdcResumeToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcResumeTokenRepository extends MongoRepository<CdcResumeToken, String> {}
