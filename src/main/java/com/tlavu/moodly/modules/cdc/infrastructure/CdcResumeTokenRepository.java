package com.tlavu.moodly.modules.cdc.infrastructure;

import com.tlavu.moodly.modules.cdc.domain.CdcResumeToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcResumeTokenRepository extends MongoRepository<CdcResumeToken, String> {
}
