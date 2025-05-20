package com.zmkn.srcc.nacos

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.listener.EventListener
import com.alibaba.nacos.api.naming.listener.NamingEvent
import com.alibaba.nacos.api.naming.pojo.Instance
import com.zmkn.log.logger.Logger
import com.zmkn.srcc.nacos.extension.isHealthy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class NacosNameResolver(
    private val namingService: NamingService,
    private val serviceName: String,
    private val groupName: String = DEFAULT_GROUP_NAME,
    private val clusters: List<String> = listOf(),
) {
    private val _logger = Logger.getInstance()
    private val _counter: AtomicInteger = AtomicInteger(0)

    private var _stopped = true

    private val _subscribeListener: EventListener = EventListener { event ->
        if (!_stopped) {
            if (event is NamingEvent) {
                reset(event.instances)
            }
        }
    }

    private fun reset(instances: List<Instance>) {
        serviceInstances[serviceName] = instances
        // 重置计数器
        _counter.set(0)
    }

    fun getWeightedInstance(
        isEphemeral: Boolean = false,
        metadata: Map<String, String> = mapOf(),
    ): Instance? {
        val instances = serviceInstances[serviceName]?.filter {
            it.isEnabled && it.isHealthy && it.isEphemeral == isEphemeral && it.metadata == metadata
        }
        return if (instances.isNullOrEmpty()) {
            null
        } else {
            // 基于权重的负载均衡
            val totalWeight = instances.sumOf { it.weight }
            var random = Random.nextDouble() * totalWeight
            instances.forEach {
                random -= it.weight
                if (random <= 0) {
                    return it
                }
            }
            instances.last()
        }
    }

    fun getRoundRobinInstance(
        isEphemeral: Boolean = false,
        metadata: Map<String, String> = mapOf(),
    ): Instance? {
        val instances = serviceInstances[serviceName]?.filter {
            it.isEnabled && it.isHealthy && it.isEphemeral == isEphemeral && it.metadata == metadata
        }
        return if (instances.isNullOrEmpty()) {
            null
        } else {
            // 基于轮询的负载均衡
            val current = _counter.getAndUpdate { prev ->
                when {
                    // 每 1000 倍实例数重置一次
                    prev >= instances.size * COUNTER_RESET_MULTIPLE -> 0
                    else -> prev + 1
                }
            }
            instances[current % instances.size]
        }
    }

    fun start(): Boolean {
        return if (namingService.isHealthy) {
            if (_stopped) {
                synchronized(this) {
                    _logger.info(serviceName, "NacosNameResolver is starting up.")
                    // 初始获取服务列表
                    reset(namingService.selectInstances(serviceName, groupName, clusters, true))
                    // 订阅服务变更
                    namingService.subscribe(serviceName, groupName, clusters, _subscribeListener)
                    _stopped = false
                    _logger.info(serviceName, "NacosNameResolver has been successfully subscribed.", "NacosNameResolver has finished starting up.")
                    true
                }
            } else {
                _logger.info(serviceName, "NacosNameResolver is already running. No need to start it again.")
                false
            }
        } else {
            _logger.info(serviceName, "NacosNameResolver has already been shut down. No need to start it again.")
            false
        }
    }

    fun refresh(): Boolean {
        return if (namingService.isHealthy) {
            if (!_stopped) {
                synchronized(this) {
                    _logger.info(serviceName, "NacosNameResolver is starting to refresh.")
                    reset(namingService.selectInstances(serviceName, groupName, clusters, true))
                    _logger.info(serviceName, "NacosNameResolver has finished refreshing.", serviceInstances[serviceName])
                    true
                }
            } else {
                _logger.info(serviceName, "NacosNameResolver is not running. Refreshing is not possible.")
                false
            }
        } else {
            _logger.info(serviceName, "NacosNameResolver has already been shut down. Refreshing is not possible.")
            false
        }
    }

    fun stop(): Boolean {
        return if (namingService.isHealthy) {
            if (!_stopped) {
                synchronized(this) {
                    _logger.info(serviceName, "NacosNameResolver is beginning to stop.")
                    // 取消订阅服务
                    namingService.unsubscribe(serviceName, groupName, clusters, _subscribeListener)
                    _stopped = true
                    _logger.info(serviceName, "NacosNameResolver has been unsubscribed.", "NacosNameResolver has been stopped.")
                    true
                }
            } else {
                _logger.info(serviceName, "NacosNameResolver is already stopped. No need to stop it again.")
                false
            }
        } else {
            _logger.info(serviceName, "NacosNameResolver has already been shut down. No need to stop it again.")
            false
        }
    }

    companion object {
        private const val COUNTER_RESET_MULTIPLE = 1000

        const val DEFAULT_SCHEME: String = "nacos"
        const val DEFAULT_GROUP_NAME = "DEFAULT_GROUP"

        // 缓存服务实例列表 Key: serviceName
        val serviceInstances: ConcurrentHashMap<String, List<Instance>> = ConcurrentHashMap()
    }
}
