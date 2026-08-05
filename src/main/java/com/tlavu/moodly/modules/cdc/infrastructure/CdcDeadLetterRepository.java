package com.tlavu.moodly.modules.cdc.infrastructure;

import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcDeadLetterRepository extends MongoRepository<CdcDeadLetter, String> {
}
