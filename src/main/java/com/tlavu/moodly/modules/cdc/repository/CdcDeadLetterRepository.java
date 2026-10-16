package com.tlavu.moodly.modules.cdc.repository;

import com.tlavu.moodly.modules.cdc.model.CdcDeadLetter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcDeadLetterRepository extends MongoRepository<CdcDeadLetter, String> {
}
