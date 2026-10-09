package com.dailyworks.apnalaundry.di

import androidx.room.Room
import com.dailyworks.apnalaundry.data.AuthRepository
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.local.AppDatabase
import com.dailyworks.apnalaundry.data.remote.AuthApi
import com.dailyworks.apnalaundry.data.billing.BillingApi
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.billing.CheckoutBridge
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.SyncApi
import com.dailyworks.apnalaundry.data.sync.SyncManager
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.screens.login.AuthViewModel
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallViewModel
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()
    }
    single { Prefs(androidContext()) }

    // One shared OkHttp-backed Ktor client for auth + sync.
    single<HttpClient> { AuthApi.defaultClient() }
    single { AuthApi(get()) }
    single { TokenManager(get(), get()) }
    single { SyncApi(get(), get()) }
    single { BillingApi(get(), get()) }
    single { BillingRepository(get()) }
    single { CheckoutBridge() }

    single { LaundryRepository(get()) }
    single { LogoStore(androidContext(), get()) }
    single { SyncManager(get(), get(), get(), get()) }
    single { SyncScheduler(get()) }
    single { AuthRepository(get(), get(), get(), get()) }

    viewModel { ShopViewModel(get(), get(), get(), get(), get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { PaywallViewModel(get(), get(), get()) }
}
