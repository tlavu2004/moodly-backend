package com.tlavu.moodly.modules.search.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CdcRetrySleeper {

	public void sleep(Duration duration) throws InterruptedException {
		Thread.sleep(duration);
	}
}
