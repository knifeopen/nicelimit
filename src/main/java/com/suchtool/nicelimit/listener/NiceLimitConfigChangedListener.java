package com.suchtool.nicelimit.listener;

import com.suchtool.nicelimit.handler.NiceLimitUrlHandler;
import com.suchtool.nicelimit.handler.NiceLimitUserCountHandler;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.util.CollectionUtils;

import java.util.Set;

/**
 * 属性更新完后调用
 * 不要用这种方法：监听EnvironmentChangeEvent，因为监听可能在环境变量更新之前被触发
 */
@Aspect
@Slf4j
public class NiceLimitConfigChangedListener {
    private final NiceLimitUrlHandler niceLimitUrlHandler;

    private final NiceLimitUserCountHandler niceLimitUserCountHandler;

    public NiceLimitConfigChangedListener(NiceLimitUrlHandler niceLimitUrlHandler,
                                          NiceLimitUserCountHandler niceLimitUserCountHandler) {
        this.niceLimitUrlHandler = niceLimitUrlHandler;
        this.niceLimitUserCountHandler = niceLimitUserCountHandler;
    }

    @Pointcut("execution(* org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder.onApplicationEvent(..))")
    public void pointcut() {

    }

    /**
     * 在属性更新完后调用
     */
    @AfterReturning(
            value = "pointcut()",
            returning = "returnValue"
    )
    public void afterReturning(JoinPoint joinPoint, Object returnValue) {
        try {
            process(joinPoint);
        } catch (Exception e) {
            log.error("nicelimit ConfigChangedListener process error", e);
        }
    }

    private void process(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        EnvironmentChangeEvent event = (EnvironmentChangeEvent) args[0];


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
                niceLimitUserCountHandler.doCheckAndUpdateConfig();
            }
        }
    }
}