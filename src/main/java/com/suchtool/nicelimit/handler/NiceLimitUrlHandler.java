package com.suchtool.nicelimit.handler;

import com.suchtool.nicelimit.dto.NiceLimitLimitedDTO;
import com.suchtool.nicelimit.property.NiceLimitForbidProperty;
import com.suchtool.nicelimit.property.NiceLimitRateLimiterProperty;
import com.suchtool.nicelimit.property.NiceLimitProperty;
import com.suchtool.nicelimit.property.NiceLimitResponseCommonProperty;
import com.suchtool.nicetool.util.base.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class NiceLimitUrlHandler {
    private NiceLimitProperty oldProperty;

    private final NiceLimitProperty newProperty;

    private final RedissonClient redissonClient;

    /**
     * key：url
     */
    private Map<String, NiceLimitForbidProperty> forbidPropertyMap;

    /**
     * key：url
     */
    private Map<String, NiceLimitRateLimiterProperty> rateLimiterPropertyMap;

    /**
     * key：url，value：RRateLimiter
     */
    private final Map<String, RRateLimiter> rateLimiterMap = new HashMap<>();

    public NiceLimitUrlHandler(NiceLimitProperty newProperty,
                               RedissonClient redissonClient) {
        this.newProperty = newProperty;
        this.redissonClient = redissonClient;
    }

    public void doCheckAndUpdateConfig() {
        if (requireUpdateLocal()) {
            if (newProperty.getDebug()) {
                log.info("nicelimit update local start");
            }
            String newConfigJsonString = JsonUtil.toJsonString(newProperty);
            // 更新本地配置
            updateLocalConfig(newConfigJsonString);

            boolean requireUpdateRemote = false;

            RBucket<String> configBucket = redissonClient.getBucket(newProperty.getConfigKey());
            String remotePropertyJson = configBucket.get();

            if (newProperty.getDebug()) {
                log.info("nicelimit fetch remote config result: {}", remotePropertyJson);
            }

            NiceLimitProperty remoteProperty = null;
            if (!StringUtils.hasText(remotePropertyJson)) {
                if (newProperty.getDebug()) {
                    log.info("nicelimit remote config is blank, update remote is required");
                }
                requireUpdateRemote = true;
            } else {
                remoteProperty = JsonUtil.toObject(remotePropertyJson, NiceLimitProperty.class);

                String newPropertyJsonString = JsonUtil.toJsonString(newProperty);
                requireUpdateRemote = !DigestUtils.md5DigestAsHex(newPropertyJsonString.getBytes())
                        .equals(DigestUtils.md5DigestAsHex(remotePropertyJson.getBytes()));
            }

            if (newProperty.getDebug()) {
                log.info("nicelimit requireUpdateRemote: {}", requireUpdateRemote);
            }

            // 更新redis的配置
            if (requireUpdateRemote) {
                RLock lock = redissonClient.getLock(newProperty.getUpdateLockKey());
                boolean locked = lock.tryLock();
                if (locked) {
                    try {
                        deleteOldRateLimiter(remoteProperty);
                        configBucket.set(newConfigJsonString);

                        if (newProperty.getDebug()) {
                            log.info("nicelimit update remote config as: {}", newProperty);
                        }
                    } catch (Exception e) {
                        log.error("nicelimit update remote error", e);
                    } finally {
                        lock.unlock();
                    }
                }
            }

            createAndRecordRateLimiter();
        }
    }

    public NiceLimitLimitedDTO checkLimit(String url) {
        if (newProperty.getDebug()) {
            log.info("nicelimit checkLimit. url:{}", url);
        }

        if (newProperty.getEnabled() == null
                || !newProperty.getEnabled()) {
            if (newProperty.getDebug()) {
                log.info("nicelimit is not enabled, don't check rate limiter. url:{}", url);
            }

            return null;
        }

        try {
            return doCheckLimit(url);
        } catch (Exception e) {
            log.error("nicelimit checkLimit error", e);
        }

        return null;
    }

    /**
     * @return 是否限流
     */
    private NiceLimitLimitedDTO doCheckLimit(String url) {
        NiceLimitLimitedDTO limitedDTO = checkByForbid(url);

        if (limitedDTO != null) {
            return limitedDTO;
        }

        return checkByRateLimit(url);
    }

    private NiceLimitLimitedDTO checkByForbid(String url) {
        if (newProperty.getDebug()) {
            log.debug("nicelimit checkByForbid start. url:{}", url);
        }

        if (CollectionUtils.isEmpty(forbidPropertyMap)) {
            if (newProperty.getDebug()) {
                log.debug("nicelimit checkByForbid not limit(forbidPropertyMap is empty), url:{}", url);
            }
            return null;
        }

        NiceLimitForbidProperty niceLimitForbidProperty = forbidPropertyMap.get(url);
        if (niceLimitForbidProperty == null) {
            if (newProperty.getDebug()) {
                log.debug("nicelimit checkByForbid not limit(forbidProperty is null), url:{}", url);
            }
            return null;
        }

        NiceLimitLimitedDTO dto = toDTO(niceLimitForbidProperty);
        log.info("nicelimit limited by checkByForbid. url:{}, return: {}", url, JsonUtil.toJsonString(dto));

        return dto;
    }

    private NiceLimitLimitedDTO checkByRateLimit(String url) {
        if (newProperty.getDebug()) {
            log.debug("nicelimit checkByRateLimit start. url:{}", url);
        }

        NiceLimitRateLimiterProperty niceLimitRateLimiterProperty = null;
        // 如果没有限流配置，则不限流
        if (!CollectionUtils.isEmpty(rateLimiterPropertyMap)) {
            niceLimitRateLimiterProperty = rateLimiterPropertyMap.get(url);
            if (niceLimitRateLimiterProperty == null) {
                if (newProperty.getDebug()) {
                    log.debug("nicelimit rateLimit limit is not required: url{} is not in rateLimiter config", url);
                }
                return null;
            }
        }

        if (niceLimitRateLimiterProperty == null) {
            return null;
        }

        RRateLimiter rateLimiter = rateLimiterMap.get(url);
        if (rateLimiter == null) {
            log.info("nicelimit rateLimiter is null, recreate start. url:{}", url);
            rateLimiter = doCreateRateLimiter(niceLimitRateLimiterProperty);
        }

        if (rateLimiter != null) {
            boolean limited = !rateLimiter.tryAcquire();
            if (limited) {
                NiceLimitRateLimiterProperty rateLimiterProperty =
                        CollectionUtils.isEmpty(rateLimiterPropertyMap)
                                ? null
                                : rateLimiterPropertyMap.get(url);
                NiceLimitLimitedDTO dto = toDTO(rateLimiterProperty);
                log.info("nicelimit limited by checkByRateLimit. url:{}, return: {}", url, JsonUtil.toJsonString(dto));
                return dto;
            } else {
                return null;
            }
        } else {
            // 正常不会到这里，为了保险，在这里不限流
            log.error("nicelimit rateLimiter is null, even though recreate. url:{}", url);
            return null;
        }
    }

    /**
     * @return 是否需要更新本地
     */
    private boolean requireUpdateLocal() {
        if (oldProperty == null) {
            return true;
        } else {
            String newPropertyJsonString = JsonUtil.toJsonString(newProperty);
            String oldPropertyJsonString = JsonUtil.toJsonString(oldProperty);

            return !DigestUtils.md5DigestAsHex(newPropertyJsonString.getBytes())
                    .equals(DigestUtils.md5DigestAsHex(oldPropertyJsonString.getBytes()));
        }
    }

    private void updateLocalConfig(String newConfigJsonString) {
        oldProperty = JsonUtil.toObject(newConfigJsonString, NiceLimitProperty.class);

        updateLocalForbidPropertyMap();

        updateLocalRateLimiterPropertyMap();
    }

    private void deleteOldRateLimiter(NiceLimitProperty remoteProperty) {
        if (newProperty.getDebug()) {
            log.info("nicelimit delete old rate limiter start");
        }

        if (remoteProperty == null) {
            if (newProperty.getDebug()) {
                log.info("nicelimit don't delete old rate limiter(remote property is null,)");
            }
            return;
        }

        List<NiceLimitRateLimiterProperty> detailList = remoteProperty.getRateLimit();
        if (CollectionUtils.isEmpty(detailList)) {
            if (newProperty.getDebug()) {
                log.info("nicelimit don't delete old rate limiter(remote property detail is empty)");
            }
            return;
        }

        for (NiceLimitRateLimiterProperty detailProperty : detailList) {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(
                    buildRateLimiterKey(remoteProperty, detailProperty.getUrl()));
            rateLimiter.delete();
            if (newProperty.getDebug()) {
                log.info("nicelimit delete old rate limiter successfully, detail property: {}",
                        JsonUtil.toJsonString(detailProperty)
                );
            }
        }
    }

    private void createAndRecordRateLimiter() {
        if (newProperty.getDebug()) {
            log.info("nicelimit create new rate limiter start");
        }

        rateLimiterMap.clear();
        if (newProperty.getDebug()) {
            log.info("nicelimit clear old rateLimiterMap");
        }

        List<NiceLimitRateLimiterProperty> detailList = newProperty.getRateLimit();
        if (CollectionUtils.isEmpty(detailList)) {
            if (newProperty.getDebug()) {
                log.info("nicelimit don't create new rate limiter(detail property is empty)");
            }
            return;
        }

        for (NiceLimitRateLimiterProperty detailProperty : detailList) {
            doCreateRateLimiter(detailProperty);
        }
    }

    private String buildRateLimiterKey(NiceLimitProperty property,
                                       String url) {
        return property.getRateLimiterKeyPrefix() + ":" + url;
    }

    private RRateLimiter doCreateRateLimiter(NiceLimitRateLimiterProperty detailProperty) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(
                buildRateLimiterKey(newProperty, detailProperty.getUrl()));
        // 返回true表示新建，false表示已存在
        boolean createNew = rateLimiter.trySetRate(
                detailProperty.getRateType(),
                detailProperty.getRate(),
                detailProperty.getRateInterval().getSeconds(),
                RateIntervalUnit.SECONDS
        );
        rateLimiterMap.put(detailProperty.getUrl(), rateLimiter);

        if (newProperty.getDebug()) {
            log.info("nicelimit create new rate limiter successfully, detail property: {}",
                    JsonUtil.toJsonString(detailProperty));
        }

        return rateLimiter;
    }

    private void updateLocalForbidPropertyMap() {
        List<NiceLimitForbidProperty> forbidProperties = newProperty.getForbid();
        if (!CollectionUtils.isEmpty(forbidProperties)) {
            forbidPropertyMap = forbidProperties.stream()
                    .collect(Collectors.toMap(NiceLimitForbidProperty::getUrl, Function.identity()));
            if (newProperty.getDebug()) {
                log.info("nicelimit updateLocalForbidPropertyMap as: {}", JsonUtil.toJsonString(forbidPropertyMap));
            }
        }
    }

    private void updateLocalRateLimiterPropertyMap() {
        List<NiceLimitRateLimiterProperty> rateLimiterProperties = newProperty.getRateLimit();
        if (!CollectionUtils.isEmpty(rateLimiterProperties)) {
            rateLimiterPropertyMap = rateLimiterProperties.stream()
                    .collect(Collectors.toMap(NiceLimitRateLimiterProperty::getUrl, Function.identity()));
            if (newProperty.getDebug()) {
                log.info("nicelimit updateLocalRateLimiterPropertyMap as: {}", JsonUtil.toJsonString(rateLimiterPropertyMap));
            }
        }
    }

    private NiceLimitLimitedDTO toDTO(NiceLimitResponseCommonProperty detailProperty) {
        Integer limitedStatusCode = newProperty.getLimitedStatusCode();
        String limitedContentType = newProperty.getLimitedContentType();
        String limitedMessage = newProperty.getLimitedMessage();

        if (detailProperty != null) {
            if (detailProperty.getLimitedStatusCode() != null) {
                limitedStatusCode = detailProperty.getLimitedStatusCode();
            }

            if (StringUtils.hasText(detailProperty.getLimitedContentType())) {
                limitedContentType = detailProperty.getLimitedContentType();
            }

            if (StringUtils.hasText(detailProperty.getLimitedMessage())) {
                limitedMessage = detailProperty.getLimitedMessage();
            }
        }

        NiceLimitLimitedDTO niceLimitLimitedDTO = new NiceLimitLimitedDTO();
        niceLimitLimitedDTO.setLimitedStatusCode(limitedStatusCode);
        niceLimitLimitedDTO.setLimitedContentType(limitedContentType);
        niceLimitLimitedDTO.setLimitedMessage(limitedMessage);

        return niceLimitLimitedDTO;
    }
}
