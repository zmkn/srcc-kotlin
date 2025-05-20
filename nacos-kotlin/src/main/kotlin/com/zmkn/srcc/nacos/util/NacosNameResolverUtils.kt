package com.zmkn.srcc.nacos.util

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.pojo.Instance
import com.zmkn.srcc.nacos.NacosNameResolver
import com.zmkn.srcc.nacos.NacosNameResolver.Companion.DEFAULT_GROUP_NAME
import java.util.concurrent.ConcurrentHashMap

object NacosNameResolverUtils {
    val resolverInstances: ConcurrentHashMap<String, NacosNameResolver> = ConcurrentHashMap()

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

    fun start(serviceName: String): Boolean = getResolver(serviceName)?.start() ?: false

    fun refresh(serviceName: String): Boolean = getResolver(serviceName)?.refresh() ?: false

    fun shutdown(serviceName: String): Boolean = getResolver(serviceName)?.shutdown() ?: false
}
