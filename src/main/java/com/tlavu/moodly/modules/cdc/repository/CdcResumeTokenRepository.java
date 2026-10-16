package com.tlavu.moodly.modules.cdc.repository;

import com.tlavu.moodly.modules.cdc.model.CdcResumeToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcResumeTokenRepository extends MongoRepository<CdcResumeToken, String> {
}
