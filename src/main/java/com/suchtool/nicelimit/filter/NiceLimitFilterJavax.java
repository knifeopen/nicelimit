package com.suchtool.nicelimit.filter;

import com.suchtool.nicelimit.callback.NiceLimitCallback;
import com.suchtool.nicelimit.dto.NiceLimitLimitedDTO;
import com.suchtool.nicelimit.handler.NiceLimitUrlHandler;
import com.suchtool.nicelimit.handler.NiceLimitUserCountHandler;
import com.suchtool.nicelimit.property.NiceLimitProperty;
import com.suchtool.nicetool.util.spring.ApplicationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class NiceLimitFilterJavax implements Filter, Ordered {
    private final Integer order;

    private final NiceLimitProperty niceLimitProperty;

    private final NiceLimitUrlHandler niceLimitUrlHandler;

    private final NiceLimitUserCountHandler niceLimitUserCountHandler;

    public NiceLimitFilterJavax(Integer order,
                                  NiceLimitProperty niceLimitProperty,
                                  NiceLimitUrlHandler niceLimitUrlHandler,
                                  NiceLimitUserCountHandler niceLimitUserCountHandler
    ) {
        this.order = order;
        this.niceLimitProperty = niceLimitProperty;
        this.niceLimitUrlHandler = niceLimitUrlHandler;
        this.niceLimitUserCountHandler = niceLimitUserCountHandler;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws ServletException, IOException {
        try {
            boolean limited = process(servletRequest, servletResponse, filterChain);
            // 如果被限流，直接返回。（process里已经处理了响应）
            if (limited) {
                return;
            }
        } catch (Exception e) {
            log.error("nicelimit filter error", e);
        }

        // 调用filter链中的下一个filter
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean process(ServletRequest servletRequest,
                            ServletResponse servletResponse,
                            FilterChain filterChain) throws IOException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            String url = httpServletRequest.getRequestURI();

            NiceLimitLimitedDTO niceLimitLimitedDTO = niceLimitUrlHandler.checkLimit(url);

            if (niceLimitLimitedDTO == null
                    && niceLimitProperty.getUserCountLimit() != null
                    && Boolean.TRUE.equals(niceLimitProperty.getUserCountLimit().getEnabled())) {
                NiceLimitCallback niceLimitCallback = ApplicationContextHolder.getContext()
                        .getBeanProvider(NiceLimitCallback.class).getIfAvailable();
                if (niceLimitCallback != null && StringUtils.hasText(niceLimitCallback.provideUserId())) {
                    niceLimitLimitedDTO = niceLimitUserCountHandler.checkLimit(niceLimitCallback.provideUserId());
                }
            }

            if (niceLimitLimitedDTO != null) {
                if (servletResponse instanceof HttpServletResponse) {
                    HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                    httpServletResponse.setStatus(niceLimitLimitedDTO.getLimitedStatusCode());
                    httpServletResponse.setContentType(niceLimitLimitedDTO.getLimitedContentType());
                    httpServletResponse.getWriter().write(niceLimitLimitedDTO.getLimitedMessage());

                    return true;
                } else {
                    throw new RuntimeException(niceLimitLimitedDTO.getLimitedMessage());
                }
            }
        }

        return false;
    }
}
