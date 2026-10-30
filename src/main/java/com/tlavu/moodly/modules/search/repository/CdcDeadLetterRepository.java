package com.tlavu.moodly.modules.search.repository;

import com.tlavu.moodly.modules.search.model.mongodb.CdcDeadLetter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcDeadLetterRepository extends MongoRepository<CdcDeadLetter, String> {
}
