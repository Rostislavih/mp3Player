package org.example.mp3player.shared.di

import org.example.mp3player.data.di.dataModule
import org.example.mp3player.presentation.di.presentationModule
import org.koin.core.module.Module

expect fun getPlatformModule(): Module

fun getSharedModule(): List<Module> {
    return listOf(
        dataModule,
        presentationModule,
        getPlatformModule()
    )
}