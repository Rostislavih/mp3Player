package org.example.mp3player.shared.di

import org.example.mp3player.data.di.androidDataModule
import org.koin.core.module.Module

actual fun getPlatformModule(): Module = androidDataModule
