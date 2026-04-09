package com.suchtool.nicelimit.property;

import com.suchtool.nicelimit.constant.NiceLimitType;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

@Data
public class NiceLimitProperty{
    /**
     * 是否注入（是否注入容器）
     */
    private Boolean inject = true;

    /**
     * 是否启用（inject为true时，此配置才有效）
     */
    private Boolean enabled = true;

    /**
     * 类型
     */
    private NiceLimitType type;

    /**
     * 是否启用调试模式
     */
    private Boolean debug = false;

    /**
     * 被限流的状态码
     */
    private Integer limitedStatusCode = 429;

    /**
     * 被限流的内容类型
     */
    private String limitedContentType = "text/plain;charset=UTF-8";

    /**
     * 被限流的提示信息
     */
    private String limitedMessage = "哎呀，访问量好大，请稍后再试试吧~";

    /**
     * 配置的key
     */
    private String configKey = "nicelimit:config";

    /**
     * 更新时用的锁的key（异步加锁，不影响业务性能）
     */
    private String updateLockKey = "nicelimit:update-lock";

    /**
     * 限流器的key前缀
     */
    private String rateLimiterKeyPrefix = "nicelimit:rate-limiter";

    /**
     * 用户数量限制（并发在线用户数）
     */
    @NestedConfigurationProperty
    private NiceLimitUserCountLimitProperty userCountLimit;


    /**
     * 禁止访问
     */
    private List<NiceLimitForbidProperty> forbid;

    /**
     * 限流
     */
    private List<NiceLimitRateLimiterProperty> rateLimit;

    /**
     * 过滤器配置
     */
    private NiceLimitFilterProperty filter;

}
