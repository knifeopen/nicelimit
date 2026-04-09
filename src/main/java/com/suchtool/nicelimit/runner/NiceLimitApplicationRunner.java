package com.suchtool.nicelimit.runner;

import com.suchtool.nicelimit.handler.NiceLimitUrlHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Slf4j
public class NiceLimitApplicationRunner implements ApplicationRunner {

    private final NiceLimitUrlHandler niceLimitUrlHandler;

    public NiceLimitApplicationRunner(NiceLimitUrlHandler niceLimitUrlHandler) {
        this.niceLimitUrlHandler = niceLimitUrlHandler;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            niceLimitUrlHandler.doCheckAndUpdateConfig();
        } catch (Exception e) {
            log.error("nicelimit application runner error", e);
        }
    }
}
