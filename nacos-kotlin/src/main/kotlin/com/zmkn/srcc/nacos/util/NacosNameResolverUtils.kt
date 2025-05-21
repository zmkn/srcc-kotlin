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

    // 定时刷新任务缓存
    private var _autoRefreshTaskExecutor: ScheduledThreadPoolExecutor? = null

    private const val AUTO_REFRESH_TASK_PERIOD: Long = 300

    val resolverInstances: ConcurrentHashMap<String, NacosNameResolver> = ConcurrentHashMap()

    fun getResolver(serviceName: String): NacosNameResolver? = resolverInstances[serviceName]

    fun getWeightedInstance(
        serviceName: String,
        metadata: Map<String, String> = mapOf(),
        isEphemeral: Boolean? = null,
    ): Instance? = getResolver(serviceName)?.getWeightedInstance(
        metadata = metadata,
        isEphemeral = isEphemeral,
    )

    fun getRoundRobinInstance(
        serviceName: String,
        metadata: Map<String, String> = mapOf(),
        isEphemeral: Boolean? = null,
    ): Instance? = getResolver(serviceName)?.getRoundRobinInstance(
        metadata = metadata,
        isEphemeral = isEphemeral,
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

    fun stopAllResolver(): List<Boolean> = resolverInstances.values.map {
        it.stop()
    }

    fun startupAutoRefreshTask(period: Long = AUTO_REFRESH_TASK_PERIOD): Boolean {
        return if (_autoRefreshTaskExecutor == null || _autoRefreshTaskExecutor!!.isShutdown) {
            _logger.info("AutoRefreshTask is beginning to startup.")
            _autoRefreshTaskExecutor = ScheduledThreadPoolExecutor(1).apply {
                scheduleAtFixedRate({
                    resolverInstances.values.forEach {
                        it.refresh()
                    }
                }, period, period, TimeUnit.SECONDS)
            }
            _logger.info("AutoRefreshTask has been startup.")
            true
        } else {
            _logger.info("AutoRefreshTask has already been startup. No need to startup it again.")
            false
        }
    }

    fun shutdownAutoRefreshTask(): Boolean {
        return if (_autoRefreshTaskExecutor == null) {
            _logger.info("AutoRefreshTask has never been started.")
            false
        } else {
            if (_autoRefreshTaskExecutor!!.isShutdown) {
                _logger.info("AutoRefreshTask has already been shut down. No need to shut it down again.")
                false
            } else {
                _logger.info("AutoRefreshTask is beginning to shut down.")
                _autoRefreshTaskExecutor!!.shutdownNow()
                _logger.info("AutoRefreshTask has been shut down.")
                true
            }
        }
    }
}
