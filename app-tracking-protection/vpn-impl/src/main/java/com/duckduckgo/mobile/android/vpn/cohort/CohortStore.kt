/*
 * Copyright (c) 2021 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.mobile.android.vpn.cohort

import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.checkMainThread
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter

interface CohortStore {
    /**
     * @return the stored cohort local date or [null] if never set
     */
    @WorkerThread
    fun getCohortStoredLocalDate(): LocalDate?

    /**
     * Stores the cohort [LocalDate] passed as parameter
     */
    @WorkerThread
    fun setCohortLocalDate(localDate: LocalDate)
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = CohortStore::class,
)
@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class RealCohortStore @Inject constructor(
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : CohortStore, VpnServiceCallbacks {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val preferences: SharedPreferences
        get() = sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = true)

    override fun getCohortStoredLocalDate(): LocalDate? {
        if (appBuildConfig.isInternalBuild()) {
            checkMainThread()
        }

        return preferences.getString(KEY_COHORT_LOCAL_DATE, null)?.let {
            LocalDate.parse(it)
        }
    }

    override fun setCohortLocalDate(localDate: LocalDate) {
        if (appBuildConfig.isInternalBuild()) {
            checkMainThread()
        }

        preferences.edit { putString(KEY_COHORT_LOCAL_DATE, formatter.format(localDate)) }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)) {
                // skip if already stored
                getCohortStoredLocalDate()?.let { return@launch }

                setCohortLocalDate(LocalDate.now())
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        // noop
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.atp.cohort.prefs"
        private const val KEY_COHORT_LOCAL_DATE = "KEY_COHORT_LOCAL_DATE"
    }
}
