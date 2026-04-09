# nicelimit

## 1.介绍

nicelimit：限流工具，零代码（基于Redisson，稳定！）。

**特性**

1. 极致的性能，对业务几乎零损耗。
2. 支持的限流功能：接口URL、静态路径URL、用户数量限制。
3. 支持两种模式：单实例、所有实例（分布式）。
4. 支持动态更新配置（需搭配Nacos或Apollo）。
5. 无需写代码，完全由配置文件控制。
6. 支持自定义限流的返回：状态码、ContentType、提示语。
7. URL支持两种限制模式：禁止访问（一个都不允许）、限流。
8. 支持：SpringBoot2、SpringBoot3

## 2.快速使用

### 2.1 引入依赖
```xml
<dependency>
    <groupId>com.suchtool</groupId>
    <artifactId>nicelimit-spring-boot-starter</artifactId>
    <version>{newest-version}</version>
</dependency>
```

### 2.2 配置Redis

添加Redisson相关配置，比如：
```
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: 222333
```
### 2.3 配置nicelimit

目标：
1. 禁止访问/aa/aaa1，返回状态码为200，json格式返回
2. 禁止访问/aa/aaa2，返回状态码为429，text/plain格式返回
3. 限流：/bb/bbb1，10秒内只能有5个请求，若超过，则返回状态码为200，json格式返回
4. 限流：/bb/bbb2，5秒内只能有10个请求，若超过，则返回状态码为429，text/plain格式返回
5. 用户数量限制。30分钟只能有1000个用户，若超过，则返回状态码为200，json格式返回

配置如下：

```
suchtool:
  nicelimit:
    inject: true
    enabled: true
    # 此项必须指定
    type: SERVLET
    config-key: "${app.id}:nicelimit:update-lock"
    update-lock-key: "${app.id}:nicelimit:update-lock"
    rate-limiter-key-prefix: "${app.id}:nicelimit:rate-limiter"
    limited-status-code: 429
    limited-content-type: "text/plain;charset=UTF-8"
    limited-message: '哎呀，访问量好大，请稍后再试试吧~'
    # 用户数量限制
    user-count-limit:
      enabled: true
      max-user-count: 1000
      time-window: 30s
      limiter-key-prefix: "{${app.id}:nicelimit:user-count-limit}"
      limited-status-code: 200
      limited-content-type: "application/json;charset=UTF-8"
      limited-message: '{"code":1,"msg":"访问量大，请稍后再试~","data":null}'
    # 禁止访问
    forbid:
      -
        url: /aa/aaa1
        limited-status-code: 200
        limited-content-type: "application/json;charset=UTF-8"
        limited-message: '{"code":1,"msg":"哎呀，访问量好大，请稍后再试试吧~","data":null}'
      -
        url: /aa/aaa2
    # 限流
    rate-limit:
      -
        url: /bb/bbb1
        rate-type: OVERALL
        rate-interval: 10s
        rate: 5
        limited-status-code: 200
        limited-content-type: "application/json;charset=UTF-8"
        limited-message: '{"code":1,"msg":"哎呀，访问量好大，请稍后再试试吧~","data":null}'
      -
        url: /bb/bbb2
        rate-type: OVERALL
        rate-interval: 5s
        rate: 10

```

### 4.配置大全

支持yml等配置方式。

#### 4.1总览

| 配置                  | 描述      | 默认值                |
|-----------------------|----------|-----------------------|
| suchtool.nicelimit.inject        | 是否注入（是否注入容器）            | true |
| suchtool.nicelimit.enabled       | 是否启用（inject为true时，才有效）  | true |
| suchtool.nicelimit.debug         | 是否启用调试模式    | false |
| suchtool.nicelimit.type          | 类型。必须指定。目前只支持SERVLET，后续会支持gateway等 | null |
| suchtool.nicelimit.limited-status-code   | 被限制时的状态码（包括：禁止访问、限流） | 429 |
| suchtool.nicelimit.limited-content-type  | 被限制时内容类型    | text/plain;charset=UTF-8   |
| suchtool.nicelimit.limited-message       | 被限制时提示信息    | 哎呀，访问量好大，请稍后再试试吧~  |
| suchtool.nicelimit.config-key            | 配置的key          | nicelimit:config                 |
| suchtool.nicelimit.update-lock-key       | 更新配置时锁的key（不影响业务性能）| nicelimit:update-lock |
| suchtool.nicelimit.rate-limiter-key-prefix | 限流器的key前缀  | nicelimit:rate-limiter |
| suchtool.nicelimit.filter      | 过滤器配置。suchtool.nicelimit.type为SERVLET时可指定 | null |
| suchtool.nicelimit.forbid      | 禁止访问的配置  | null  |
| suchtool.nicelimit.rate-limit  | 限流的配置  | null  |
| suchtool.nicelimit.user-count-limit      | 用户数量限制  | null  |

#### 4.2 过滤器

如果suchtool.nicelimit.type为SERVLET，则可以配置过滤器。

suchtool.nicelimit.filter的配置：

| 配置           | 描述                   | 默认值     |
|----------------|-----------------------|------------|
| filter-order   | 过滤器顺序       | 100            |

#### 4.3 禁止访问

suchtool.nicelimit.forbid配置：

| 配置           | 描述                | 默认值     |
|----------------|--------------------|------------|
| url            | URL（不支持通配符，为了极致的效率）| null |
| limited-status-code   | 被限制时的状态码   | null  |
| limited-content-type  | 被限制时的内容类型 | null  |
| limited-message       | 被限制时的提示信息 | null  |

如果本处的limited-status-code、limited-content-type、limited-message没配置，则取顶层（suchtool.nicelimit.xxx）的配置。

#### 4.4 限流

suchtool.nicelimit.rate-limit配置：

| 配置           | 描述                   | 默认值     |
|----------------|-----------------------|------------|
| url            | URL（不支持通配符，为了极致的效率） | null |
| rate-type      | 速度类型：OVERALL（全实例），PER_CLIENT（单实例） | null |
| rate-interval  | 速度间隔（单位时间）。比如：10s   | null |
| rate           | 速度（数量）           | null |
| limited-status-code   | 被限制时的状态码      | null |
| limited-content-type  | 被限制时的内容类型    | null |
| limited-message       | 被限制时的提示信息    | null |

如果本处的limited-status-code、limited-content-type、limited-message没配置，则取顶层（suchtool.nicelimit.xxx）的配置。


#### 4.4 用户数量限制

suchtool.nicelimit.user-count-limit配置：

| 配置           | 描述                   | 默认值     |
|----------------|-----------------------|------------|
| enabled        | 是否启用 | false |
| max-user-count | 最大用户数量 | 1000 |
| time-window    | 时间窗口     | 30m |
| limiter-key-prefix  | 用户数量限制器的key前缀  | nicelimit:user-count-limit |
| bucket-count  | 桶的数量（若并发量大，可适当增加）  | 1 |
| limited-status-code | 被限制时的状态码      | null |
| limited-content-type| 被限制时的内容类型    | null |
| limited-message     | 被限制时的提示信息    | null |

如果本处的limited-status-code、limited-content-type、limited-message没配置，则取顶层（suchtool.nicelimit.xxx）的配置。

有两种方法提供userId

**法1：** 实现NiceLimitCallback#provideUserId(String userId)

此法适用于使用过滤器鉴权的项目。需配合：suchtool.nicelimit.filter.filter-order

```java
@Component
public class CustomNiceLimitCallbackImpl implements NiceLimitCallback{
    @Override
    public String provideUserId() {
        // 这里可以写自己项目获取用户ID的方法
        return null;
    }
}
```

**法2：** 手动调用NiceLimitUserCountHandler#checkLimit(String userId)

此法适用于任意项目，在鉴权的地方加下边这行代码即可

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YourClass {
    @Autowired
    private NiceLimitUserCountHandler niceLimitUserCountHandler;

    public void checkAuth(String userId) {
        niceLimitUserCountHandler.checkLimit(userId);
    }
}

```

## 5.原理

禁止访问：suchtool.nicelimit.forbid会禁止访问。
限流：suchtool.nicelimit.rate-limit使用Redisson的RRateLimiter进行限流。
用户人数限制：Redis的ZSET+滑动窗口+LUA脚本。

SERVLET类型：注入一个Filter，对请求进行处理。