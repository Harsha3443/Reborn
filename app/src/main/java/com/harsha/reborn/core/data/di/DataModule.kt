package com.harsha.reborn.core.data.di

import android.content.Context
import androidx.room.Room
import com.harsha.reborn.core.data.local.BlockingRulesDao
import com.harsha.reborn.core.data.local.FrictionDao
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.SelectedAppDao
import com.harsha.reborn.core.data.local.StrictModeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideRebornDatabase(@ApplicationContext context: Context): RebornDatabase =
        Room.databaseBuilder(
            context,
            RebornDatabase::class.java,
            "reborn_database.db",
        )
            .addMigrations(
                RebornDatabase.MIGRATION_1_2,
                RebornDatabase.MIGRATION_2_3,
                RebornDatabase.MIGRATION_3_4,
                RebornDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    @Singleton
    fun provideSelectedAppDao(database: RebornDatabase): SelectedAppDao =
        database.selectedAppDao()

    @Provides
    @Singleton
    fun provideBlockingRulesDao(database: RebornDatabase): BlockingRulesDao =
        database.blockingRulesDao()

    @Provides
    @Singleton
    fun provideFrictionDao(database: RebornDatabase): FrictionDao =
        database.frictionDao()

    @Provides
    @Singleton
    fun provideStrictModeDao(database: RebornDatabase): StrictModeDao =
        database.strictModeDao()
}
