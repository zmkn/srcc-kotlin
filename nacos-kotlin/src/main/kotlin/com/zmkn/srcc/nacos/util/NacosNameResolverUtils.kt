package com.zmkn.srcc.nacos.util

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.pojo.Instance
import com.zmkn.log.logger.Logger
import com.zmkn.srcc.nacos.NacosNameResolver
import com.zmkn.srcc.nacos.NacosNameResolver.Companion.DEFAULT_GROUP_NAME
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object NacosNameResolverUtils {
    private val _logger = Logger.getInstance()

    // 定时刷新缓存
    private lateinit var _autoRefreshTaskExecutor: ScheduledThreadPoolExecutor

    private const val AUTO_REFRESH_TASK_PERIOD: Long = 5

    val resolverInstances: ConcurrentHashMap<String, NacosNameResolver> = ConcurrentHashMap()

    init {
        startupAutoRefreshTask()
        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
    }

    private fun startupAutoRefreshTask(period: Long = AUTO_REFRESH_TASK_PERIOD) {
        _autoRefreshTaskExecutor = ScheduledThreadPoolExecutor(1).apply {
            scheduleAtFixedRate({
                resolverInstances.values.forEach {
                    it.refresh()
                }
            }, period, period, TimeUnit.MINUTES)
        }
    }

    fun shutdownAutoRefreshTask(): Boolean {
        return if (_autoRefreshTaskExecutor.isShutdown) {
            _logger.info("AutoRefreshTaskExecutor has already been shut down. No need to shut it down again.")
            false
        } else {
            _logger.info("AutoRefreshTaskExecutor is beginning to shut down.")
            _autoRefreshTaskExecutor.shutdownNow()
            _logger.info("AutoRefreshTaskExecutor has been shut down.")
            true
        }
    }

    fun getResolver(serviceName: String): NacosNameResolver? = resolverInstances[serviceName]

    fun getWeightedInstance(
        serviceName: String,
        isEphemeral: Boolean = false,
        metadata: Map<String, String> = mapOf(),
    ): Instance? = getResolver(serviceName)?.getWeightedInstance(
        isEphemeral = isEphemeral,
        metadata = metadata,
    )

    fun getRoundRobinInstance(
        serviceName: String,
        isEphemeral: Boolean = false,
        metadata: Map<String, String> = mapOf(),
    ): Instance? = getResolver(serviceName)?.getRoundRobinInstance(
        isEphemeral = isEphemeral,
        metadata = metadata,
    )

    fun initResolver(
        namingService: NamingService,
        serviceName: String,
        groupName: String = DEFAULT_GROUP_NAME,
        clusters: List<String> = listOf(),
    ): NacosNameResolver {
        return resolverInstances[serviceName] ?: synchronized(this) {
            NacosNameResolver(
                namingService = namingService,
                serviceName = serviceName,
                groupName = groupName,
                clusters = clusters,
            ).also {
                resolverInstances[serviceName] = it
            }
        }
    }

    fun startResolver(serviceName: String): Boolean = getResolver(serviceName)?.start() ?: false

    fun refreshResolver(serviceName: String): Boolean = getResolver(serviceName)?.refresh() ?: false

    fun stopResolver(serviceName: String): Boolean = getResolver(serviceName)?.stop() ?: false

    fun shutdownResolver(serviceName: String): Boolean = getResolver(serviceName)?.shutdown() ?: false

    fun shutdown() {
        // 立即终止定时任务
        shutdownAutoRefreshTask()
        resolverInstances.values.forEach {
            it.shutdown()
        }
    }
}
