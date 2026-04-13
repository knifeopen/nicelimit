package com.suchtool.nicelimit.handler;

import com.suchtool.nicelimit.dto.NiceLimitLimitedDTO;
import com.suchtool.nicelimit.property.NiceLimitProperty;
import com.suchtool.nicelimit.property.NiceLimitUserCountLimitProperty;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class NiceLimitUserCountHandler {
    /**
     * KEYS[1] 用户滑动窗口 ZSET，member=userId，score=该用户最近命中的窗口时间戳（毫秒）。
     * ARGV[1] userId；ARGV[2] nowMillis；ARGV[3] timeWindowMillis；ARGV[4] maxCount。
     * 返回值：1 允许并已更新；0 拒绝（当前用户不在有效窗口且已达最大用户数）。
     */
    private static final String CHECK_AND_SAVE_LUA = "" +
            "local key = KEYS[1]\n" +
            "local userId = ARGV[1]\n" +
            "local now = tonumber(ARGV[2])\n" +
            "local timeWindowMs = tonumber(ARGV[3])\n" +
            "local maxCount = tonumber(ARGV[4])\n" +
            "-- 【强制清理所有过期数据】\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - timeWindowMs)\n" +
            "-- 【统计清理后的有效总人数】\n" +
            "local validCount = redis.call('ZCARD', key)\n" +
            "-- 【判断用户是否已存在】\n" +
            "local exists = redis.call('ZSCORE', key, userId)\n" +
            "if exists then\n" +
            "    -- 已存在：刷新时间，放行\n" +
            "    redis.call('ZADD', key, now, userId)\n" +
            "    -- 设置过期时间 = 窗口大小，防止key残留\n" +
            "    redis.call('EXPIRE', key, timeWindowMs / 1000)\n" +
            "    return 1\n" +
            "end\n" +
            "-- 【不存在：判断有效人数是否超限】\n" +
            "if validCount >= maxCount then\n" +
            "    -- 已满员，拒绝\n" +
            "    return 0\n" +
            "end\n" +
            "-- 未满员，添加新用户\n" +
            "redis.call('ZADD', key, now, userId)\n" +
            "-- 设置过期时间 = 窗口大小，防止key残留\n" +
            "redis.call('EXPIRE', key, timeWindowMs / 1000)\n" +
            "return 1";

    private final NiceLimitProperty niceLimitProperty;

    private final RedissonClient redissonClient;

    public NiceLimitUserCountHandler(NiceLimitProperty niceLimitProperty,
                                  RedissonClient redissonClient) {
        this.niceLimitProperty = niceLimitProperty;
        this.redissonClient = redissonClient;
    }

    public void doCheckAndUpdateConfig() {
        NiceLimitUserCountLimitProperty userCountLimitProperty = niceLimitProperty.getUserCountLimit();
        if (userCountLimitProperty != null
                && !Boolean.TRUE.equals(userCountLimitProperty.getEnabled())) {

            String limiterKeyPrefix = userCountLimitProperty.getLimiterKeyPrefix();
            RKeys keys = redissonClient.getKeys();
            long delete = keys.deleteByPattern(limiterKeyPrefix + "*");
            log.info("nicelimit limit-user-count key{} is deleted successfully", limiterKeyPrefix);
        }
    }

    /**
     * @return null 表示未限流；非 null 表示被限流
     */
    public NiceLimitLimitedDTO checkLimit(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }

        NiceLimitUserCountLimitProperty userCountLimitProperty = niceLimitProperty.getUserCountLimit();
        if (userCountLimitProperty == null) {
            return null;
        }

        if (!Boolean.TRUE.equals(userCountLimitProperty.getEnabled())) {
            return null;
        }

        long nowMillis = System.currentTimeMillis();
        long timeWindowMs = userCountLimitProperty.getTimeWindow().toMillis();

        // 分桶路由：userId 取模 → 自动打散 slot
        int bucketId = userId.hashCode() % userCountLimitProperty.getBucketCount();
        if (bucketId < 0) {
            bucketId = -bucketId;
        }
        // 每个桶成员的最大数量
        int bucketMemberMaxCountNumber = userCountLimitProperty.getMaxUserCount() / userCountLimitProperty.getBucketCount();
        String bucketMemberMaxCount = String.valueOf(bucketMemberMaxCountNumber);
        String keyPrefix = userCountLimitProperty.getLimiterKeyPrefix() + ":bucket" + bucketId;

        // 只返回 Lua 整型结果，使用 LongCodec。
        RScript script = redissonClient.getScript(LongCodec.INSTANCE);
        List<Object> keys = Arrays.asList(keyPrefix);

        Long allowed = script.eval(
                RScript.Mode.READ_WRITE,
                CHECK_AND_SAVE_LUA,
                RScript.ReturnType.INTEGER,
                keys,
                userId,
                nowMillis,
                timeWindowMs,
                bucketMemberMaxCount
        );

        if (allowed != null && allowed.intValue() == 0) {
            log.info("nicelimit limit-user-count：{} is limited", userId);
            return buildLimitedDto();
        }

        return null;
    }

    private NiceLimitLimitedDTO buildLimitedDto() {
        NiceLimitUserCountLimitProperty userCountLimitProperty = niceLimitProperty.getUserCountLimit();

        NiceLimitLimitedDTO dto = new NiceLimitLimitedDTO();
        dto.setLimitedStatusCode(
                userCountLimitProperty.getLimitedStatusCode() != null
                ? userCountLimitProperty.getLimitedStatusCode()
                : niceLimitProperty.getLimitedStatusCode());
        dto.setLimitedContentType(
                StringUtils.hasText(userCountLimitProperty.getLimitedContentType())
                ? userCountLimitProperty.getLimitedContentType()
                : niceLimitProperty.getLimitedContentType());
        dto.setLimitedMessage(
                StringUtils.hasText(userCountLimitProperty.getLimitedMessage())
                        ? userCountLimitProperty.getLimitedMessage()
                        : niceLimitProperty.getLimitedMessage());
        return dto;
    }
}
