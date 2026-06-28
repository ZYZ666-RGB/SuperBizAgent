# SuperBizAgent 评测用运行手册

## Redis 超时

当 order-service 出现 Redis timeout 时，首先应该检查 Redis 连接池是否耗尽、超时配置、
Redis 节点健康状态、网络延迟以及重试预算。重启服务前需要保留错误日志。

## CPU 过高

当 payment-service 出现 CPU 使用率过高时，应检查热点线程、近期发布、垃圾回收、
容器 CPU 限制以及入口请求量。高风险重启前应优先评估扩容。

## 内存 OOM

当 JVM 服务出现 OOM 或内存使用率过高时，应检查堆使用量、Full GC 频率、堆转储证据、
内存泄漏模式以及近期流量变化。
