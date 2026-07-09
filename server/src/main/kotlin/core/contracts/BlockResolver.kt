package com.zula.core.contracts

interface BlockResolver {
    suspend fun resolveViewerBlock(viewerId: String?, targetUserId: String): Boolean
}
