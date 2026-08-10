package com.sphp.bootstrap.config;

import cn.dev33.satoken.stp.StpUtil;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.patient.auth.support.jwt.CJwtClaims;
import com.sphp.patient.auth.support.jwt.CJwtService;
import com.sphp.patient.common.constant.CAuthConstant;
import com.sphp.shared.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

/**
 * 在线问诊 STOMP WebSocket 配置。
 *
 * <p>REST 仍负责消息写入，WebSocket 仅提供服务端到客户端的私有实时通知。
 */
@Configuration
@EnableWebSocketMessageBroker
public class ConsultationWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** CORS 前端来源白名单 */
    private final CorsProperties corsProperties;
    /** C端 JWT 解析服务 */
    private final CJwtService cJwtService;
    /** C端刷新会话存储，用于校验 JWT 未被撤销 */
    private final StringRedisTemplate redisTemplate;
    /** B端账号查询接口 */
    private final BUserMapper bUserMapper;

    /**
     * 创建在线问诊实时连接配置。
     *
     * @param corsProperties 前端来源白名单
     * @param cJwtService C端 JWT 解析服务
     * @param redisTemplate C端登录会话存储
     * @param bUserMapper B端账号查询接口
     */
    public ConsultationWebSocketConfig(CorsProperties corsProperties, CJwtService cJwtService,
                                       StringRedisTemplate redisTemplate, BUserMapper bUserMapper) {
        this.corsProperties = corsProperties;
        this.cJwtService = cJwtService;
        this.redisTemplate = redisTemplate;
        this.bUserMapper = bUserMapper;
    }

    /**
     * 注册 STOMP 端点和私有用户消息代理。
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 注册浏览器 WebSocket 连接端点。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/consultation")
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(String[]::new));
    }

    /**
     * 校验 STOMP 建连身份并限制客户端只能订阅私有消息队列。
     *
     * @param registration 入站通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null || !"/user/queue/consultation-message".equals(accessor.getDestination())) {
                        throw new MessageDeliveryException("不允许订阅该消息通道");
                    }
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    // 消息必须通过 REST 落库，禁止绕过幂等和状态机直接走 WebSocket 写入。
                    throw new MessageDeliveryException("问诊消息请通过 REST 接口发送");
                }
                return message;
            }
        });
    }

    /**
     * 基于客户端类型验证 B/C 独立令牌并建立不可伪造的私有用户身份。
     *
     * @param accessor STOMP 连接头
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        String clientType = accessor.getFirstNativeHeader("X-Client-Type");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ") || !StringUtils.hasText(clientType)) {
            throw new MessageDeliveryException("缺少有效的实时消息身份");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if ("C".equals(clientType)) {
            CJwtClaims claims = cJwtService.parseAccessToken(token);
            String session = redisTemplate.opsForValue().get(CAuthConstant.REFRESH_SESSION_KEY_PREFIX + claims.sessionHash());
            if (!StringUtils.hasText(session) || !session.startsWith(claims.userId() + ":")) {
                throw new MessageDeliveryException("C端登录会话已失效");
            }
            accessor.setUser(principal("C:" + claims.userId()));
            return;
        }
        if ("B".equals(clientType)) {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                throw new MessageDeliveryException("B端登录令牌无效");
            }
            BUser user = bUserMapper.selectById(Long.valueOf(String.valueOf(loginId)));
            if (user == null || user.getDeletedAt() != null || !"ENABLED".equals(user.getStatus())) {
                throw new MessageDeliveryException("B端账号不可用");
            }
            accessor.setUser(principal("B:" + user.getId()));
            return;
        }
        throw new MessageDeliveryException("未知的实时消息客户端类型");
    }

    /**
     * 创建稳定的 STOMP 私有用户标识。
     *
     * @param name 用户标识
     * @return Principal 实例
     */
    private Principal principal(String name) {
        return () -> name;
    }
}
