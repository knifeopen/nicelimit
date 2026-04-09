package com.suchtool.nicelimit.listener;

import com.suchtool.nicelimit.handler.NiceLimitUrlHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.CollectionUtils;

import java.util.Set;

@Slf4j
public class NiceLimitEnvironmentChangeEventListener implements ApplicationListener<EnvironmentChangeEvent> {
    private final NiceLimitUrlHandler niceLimitUrlHandler;

    public NiceLimitEnvironmentChangeEventListener(NiceLimitUrlHandler niceLimitUrlHandler) {
        this.niceLimitUrlHandler = niceLimitUrlHandler;
    }

    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        try {
            Set<String> keys = event.getKeys();
            if (!CollectionUtils.isEmpty(keys)) {
                boolean requireUpdateConfig = false;
                for (String key : keys) {
                    if (key.startsWith("suchtool.nicelimit")) {
                        requireUpdateConfig = true;
                        break;
                    }
                }

                if (requireUpdateConfig) {
                    niceLimitUrlHandler.doCheckAndUpdateConfig();
                }
            }
        } catch (Exception e) {
            log.error("nicelimit EnvironmentChangeEventListener error", e);
        }
    }
}
