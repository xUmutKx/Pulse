package com.drdisagree.glasslayer.xposed

import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.apps.AppModule
import com.drdisagree.glasslayer.xposed.systemui.SystemUiModule
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleMain : XposedModule() {

    private var processName: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        when (param.packageName) {
            SystemUiModule.PACKAGE -> {
                if (processName != null && processName != SystemUiModule.PACKAGE) return
                if (!SystemUiModule.claim()) return
                HookEnv.init(this, param.classLoader)
                SystemUiModule.start(param.applicationInfo)
            }

            in AppModule.PACKAGES -> {
                if (processName != null && processName != param.packageName) return
                if (!AppModule.claim()) return
                HookEnv.init(this, param.classLoader)
                AppModule.start(param.packageName)
            }
        }
    }
}
