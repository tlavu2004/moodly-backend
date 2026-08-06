package com.tlavu.moodly.modules.cdc.application;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CdcRetrySleeper {

	public void sleep(Duration duration) throws InterruptedException {
		Thread.sleep(duration);
	}
}
