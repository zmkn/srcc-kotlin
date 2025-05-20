package com.zmkn.srcc.nacos.extension

import com.alibaba.nacos.api.naming.NamingService

val NamingService.isHealthy: Boolean
    get() = serverStatus == "UP"
