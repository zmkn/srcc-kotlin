package com.zmkn.srcc.nacos

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.listener.EventListener
import com.alibaba.nacos.api.naming.listener.NamingEvent
import com.alibaba.nacos.api.naming.pojo.Instance
import com.zmkn.log.logger.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class NacosNameResolver(
    private val namingService: NamingService,
    private val serviceName: String,
    private val groupName: String = DEFAULT_GROUP_NAME,
    private val clusters: List<String> = listOf(),
) {
    init {
        start()
    }

    private val _logger = Logger.getInstance()

    private var _shutdown = true

    private val _subscribeListener: EventListener = EventListener { event ->
        if (!_shutdown) {
            if (event is NamingEvent) {
                reset(event.instances)
            }
        }
    }

    private fun reset(instances: List<Instance>) {
        serviceInstances[serviceName] = instances
    }

    fun getServiceInstance(
        isEphemeral: Boolean = false,
        metadata: Map<String, String> = mapOf(),
    ): Instance {
        val instances = serviceInstances[serviceName]?.filter {
            it.isEnabled && it.isHealthy && it.isEphemeral == isEphemeral && it.metadata == metadata
        }
        return if (instances.isNullOrEmpty()) {
            throw IllegalStateException("Service $serviceName not found")
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

    fun start(): Boolean {
        return if (_shutdown) {
            synchronized(this) {
                _logger.info("NacosNameResolver is starting up.")
                // 初始获取服务列表
                refresh()
                // 订阅服务变更
                namingService.subscribe(serviceName, groupName, clusters, _subscribeListener)
                _shutdown = false
                _logger.info("NacosNameResolver has been successfully subscribed.", "NacosNameResolver has finished starting up.")
                true
            }
        } else {
            _logger.info("NacosNameResolver is already running. No need to start it again.")
            false
        }
    }

    fun refresh(): Boolean {
        return if (!_shutdown) {
            synchronized(this) {
                _logger.info("NacosNameResolver is starting to refresh.")
                reset(namingService.selectInstances(serviceName, groupName, clusters, true))
                _logger.info("NacosNameResolver has finished refreshing.", serviceInstances[serviceName])
                true
            }
        } else {
            _logger.info("NacosNameResolver is not running.")
            false
        }
    }

    fun shutdown(): Boolean {
        return if (!_shutdown) {
            synchronized(this) {
                _logger.info("NacosNameResolver is beginning to stop.")
                // 取消订阅服务
                namingService.unsubscribe(serviceName, groupName, clusters, _subscribeListener)
                _shutdown = true
                _logger.info("NacosNameResolver has been unsubscribed.", "NacosNameResolver has been stopped.")
                true
            }
        } else {
            _logger.info("NacosNameResolver is already stopped. No need to stop it again.")
            false
        }
    }

    companion object {
        const val DEFAULT_GROUP_NAME = "DEFAULT_GROUP"

        // 缓存服务实例列表 Key: serviceName
        val serviceInstances: ConcurrentHashMap<String, List<Instance>> = ConcurrentHashMap()
    }
}
