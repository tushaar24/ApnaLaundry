package com.dailyworks.apnalaundry.di

import androidx.room.Room
import com.dailyworks.apnalaundry.data.AuthRepository
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.local.AppDatabase
import com.dailyworks.apnalaundry.data.remote.AuthApi
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.screens.login.AuthViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { Prefs(androidContext()) }
    single { AuthApi() }
    single { LaundryRepository(get()) }
    single { AuthRepository(get(), get()) }

    viewModel { ShopViewModel(get(), get()) }
    viewModel { AuthViewModel(get()) }
}
