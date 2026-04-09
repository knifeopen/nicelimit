package com.suchtool.nicelimit.property;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;

@Data
@EqualsAndHashCode(callSuper = true)
public class NiceLimitUserCountLimitProperty extends NiceLimitResponseCommonProperty{
    /**
     * 是否启用
     */
    private Boolean enabled = false;


    /**
     * 用户数量限制器的key前缀
     */
    private String limiterKeyPrefix = "hlimit:user-count-limit";

    /**
     * 桶的数量（若并发量大，可适当增加）
     */
    private Integer bucketCount = 1;

    /**
     * 最大用户数量（超过则限制）
     */
    private Integer maxUserCount = 1000;

    /**
     * 时间窗口
     */
    private Duration timeWindow = Duration.ofMinutes(30);
}
