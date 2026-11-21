package com.harsha.reborn.core.engine

import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.engine.feed.FeedBlocker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EngineEntryPoint {
    fun rulesCacheManager(): RulesCacheManager
    fun rulesRepository(): BlockingRulesRepository
    fun frictionRepository(): FrictionRepository
    fun permissionHealthManager(): PermissionHealthManager
    fun quickBlockToggleController(): QuickBlockToggleController
    fun feedBlocker(): FeedBlocker
}
