package com.dailyworks.apnalaundry.di

import com.dailyworks.apnalaundry.data.AuthRepository
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import com.dailyworks.apnalaundry.data.shops.ShopsRepository
import com.dailyworks.apnalaundry.data.remote.AuthApi
import com.dailyworks.apnalaundry.data.remote.FirebasePhoneAuth
import com.dailyworks.apnalaundry.data.billing.BillingApi
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.billing.CheckoutBridge
import com.dailyworks.apnalaundry.data.experiments.ExperimentsRepository
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.SyncApi
import com.dailyworks.apnalaundry.data.sync.SyncManager
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.screens.login.AuthViewModel
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallViewModel
import com.dailyworks.apnalaundry.ui.tour.TourVoice
import com.dailyworks.apnalaundry.data.assistant.AssistantApi
import com.dailyworks.apnalaundry.ui.screens.assistant.AssistantViewModel
import com.dailyworks.apnalaundry.ui.screens.assistant.VoiceRecorder
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { Prefs(androidContext()) }
    // One Room database per shop (multi-shop); the main shop keeps AppDatabase.NAME.
    single { ShopDirectory(androidContext(), get()) }

    // One shared OkHttp-backed Ktor client for auth + sync.
    single<HttpClient> { AuthApi.defaultClient(androidContext()) }
    single { AuthApi(get()) }
    single { TokenManager(get(), get()) }
    single { SyncApi(get(), get()) }
    single { BillingApi(get(), get()) }
    single { BillingRepository(get(), get()) }
    single { CheckoutBridge() }

    single { LaundryRepository(get()) }
    single { LogoStore(androidContext(), get(), get()) }
    single { ShopsRepository(get(), get(), get()) }
    single { SyncManager(get(), get(), get(), get()) }
    single { SyncScheduler(get()) }
    single { FirebasePhoneAuth() }
    single { TourVoice(androidContext(), get()) }
    single { ExperimentsRepository(get(), get(), get()) }
    single { AssistantApi(get(), get(), get()) }
    single { com.dailyworks.apnalaundry.data.AppConfigRepository(get(), get()) }
    factory { VoiceRecorder(androidContext()) }
    single { AuthRepository(get(), get(), get(), get(), get(), get(), get(), get()) }

    viewModel { ShopViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { PaywallViewModel(get(), get(), get()) }
    viewModel { AssistantViewModel(get(), get(), get(), get(), get()) }
}
