/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailDupUser
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedSuccess
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.accountKeysFailed
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceKeysNotFoundError
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceSuccess
import com.duckduckgo.sync.TestSyncFixtures.connectKeys
import com.duckduckgo.sync.TestSyncFixtures.decryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountInvalid
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountSuccess
import com.duckduckgo.sync.TestSyncFixtures.deviceFactor
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceName
import com.duckduckgo.sync.TestSyncFixtures.deviceType
import com.duckduckgo.sync.TestSyncFixtures.encryptedRecoveryCode
import com.duckduckgo.sync.TestSyncFixtures.failedLoginKeys
import com.duckduckgo.sync.TestSyncFixtures.getDevicesError
import com.duckduckgo.sync.TestSyncFixtures.getDevicesSuccess
import com.duckduckgo.sync.TestSyncFixtures.hashedPassword
import com.duckduckgo.sync.TestSyncFixtures.invalidDecryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.listOfConnectedDevices
import com.duckduckgo.sync.TestSyncFixtures.loginFailed
import com.duckduckgo.sync.TestSyncFixtures.loginSuccess
import com.duckduckgo.sync.TestSyncFixtures.logoutInvalid
import com.duckduckgo.sync.TestSyncFixtures.logoutSuccess
import com.duckduckgo.sync.TestSyncFixtures.patchAllError
import com.duckduckgo.sync.TestSyncFixtures.patchAllSuccess
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.protectedEncryptionKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.stretchedPrimaryKey
import com.duckduckgo.sync.TestSyncFixtures.syncData
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.parser.SyncCrypter
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppSyncRepositoryTest {

    private var nativeLib: SyncLib = mock()
    private var syncDeviceIds: SyncDeviceIds = mock()
    private var syncApi: SyncApi = mock()
    private var syncStore: SyncStore = mock()
    private var syncCrypter: SyncCrypter = mock()

    private lateinit var syncRepo: SyncRepository

    @Before
    fun before() {
        syncRepo = AppSyncRepository(syncDeviceIds, nativeLib, syncApi, syncStore, syncCrypter)
    }

    @Test
    fun whenCreateAccountSucceedsThenAccountPersisted() {
        prepareToProvideDeviceIds()
        prepareForCreateAccountSuccess()

        val result = syncRepo.createAccount()

        assertEquals(Result.Success(true), result)
        verify(syncStore).userId = userId
        verify(syncStore).deviceId = deviceId
        verify(syncStore).deviceName = deviceName
        verify(syncStore).token = token
        verify(syncStore).primaryKey = primaryKey
        verify(syncStore).secretKey = secretKey
    }

    @Test
    fun whenCreateAccountFailsThenReturnError() {
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(accountCreatedFailDupUser)

        val result = syncRepo.createAccount()

        assertEquals(accountCreatedFailDupUser, result)
        verifyNoInteractions(syncStore)
    }

    @Test
    fun whenCreateAccountGenerateKeysFailsThenReturnError() {
        prepareToProvideDeviceIds()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeysFailed)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(accountCreatedSuccess)

        val result = syncRepo.createAccount()

        assertTrue(result is Result.Error)
        verifyNoInteractions(syncApi)
        verifyNoInteractions(syncStore)
    }

    @Test
    fun whenAccountExistsThenGetAccountInfoReturnData() {
        givenAuthenticatedDevice()

        val result = syncRepo.getAccountInfo()

        assertEquals(userId, result.userId)
        assertEquals(deviceId, result.deviceId)
        assertEquals(deviceName, result.deviceName)
        assertTrue(result.isSignedIn)
    }

    @Test
    fun whenAccountNotCreatedThenAccountInfoEmpty() {
        whenever(syncStore.primaryKey).thenReturn("")

        val result = syncRepo.getAccountInfo()

        assertEquals("", result.userId)
        assertEquals("", result.deviceId)
        assertEquals("", result.deviceName)
        assertFalse(result.isSignedIn)
    }

    @Test
    fun whenLogoutSucceedsThenReturnSuccessAndRemoveData() {
        givenAuthenticatedDevice()
        whenever(syncApi.logout(token, deviceId)).thenReturn(logoutSuccess)

        val result = syncRepo.logout(deviceId)

        assertTrue(result is Result.Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenLogoutFailsThenReturnError() {
        givenAuthenticatedDevice()
        whenever(syncApi.logout(token, deviceId)).thenReturn(logoutInvalid)

        val result = syncRepo.logout(deviceId)

        assertTrue(result is Result.Error)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenLogoutRemoteDeviceSucceedsThenReturnSuccessButDoNotRemoveLocalData() {
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(eq(token), anyString())).thenReturn(logoutSuccess)

        val result = syncRepo.logout("randomDeviceId")

        assertTrue(result is Success)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenDeleteAccountSucceedsThenReturnSuccessAndRemoveData() {
        givenAuthenticatedDevice()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)

        val result = syncRepo.deleteAccount()

        assertTrue(result is Result.Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenDeleteAccountFailsThenReturnError() {
        givenAuthenticatedDevice()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountInvalid)

        val result = syncRepo.deleteAccount()

        assertTrue(result is Result.Error)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenLoginSucceedsThenAccountPersisted() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        prepareForLoginSuccess()

        val result = syncRepo.login()

        assertEquals(Result.Success(true), result)
        verify(syncStore).userId = userId
        verify(syncStore).deviceId = deviceId
        verify(syncStore).deviceName = deviceName
        verify(syncStore).token = token
        verify(syncStore).primaryKey = primaryKey
        verify(syncStore).secretKey = secretKey
    }

    @Test
    fun whenLoginFromQRSucceedsThenAccountPersisted() {
        prepareForLoginSuccess()

        val result = syncRepo.login(jsonRecoveryKeyEncoded)

        assertEquals(Result.Success(true), result)
        verify(syncStore).userId = userId
        verify(syncStore).deviceId = deviceId
        verify(syncStore).deviceName = deviceName
        verify(syncStore).token = token
        verify(syncStore).primaryKey = primaryKey
        verify(syncStore).secretKey = secretKey
    }

    @Test
    fun whenRecoveryCodeNotFoudnThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(null)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenGenerateKeysFromRecoveryCodeFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        prepareToProvideDeviceIds()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(failedLoginKeys)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenGenerateKeysFromQRFailsThenReturnError() {
        prepareToProvideDeviceIds()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(failedLoginKeys)

        val result = syncRepo.login(jsonRecoveryKeyEncoded)

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenLoginFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginFailed)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenLoginFromQRFailsThenReturnError() {
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(decryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginFailed)

        val result = syncRepo.login(jsonRecoveryKeyEncoded)

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenDecryptSecretKeyFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(invalidDecryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginSuccess)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun getConnectedDevicesSucceedsThenReturnSuccess() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        val result = syncRepo.getConnectedDevices() as Success

        assertEquals(listOfConnectedDevices, result.data)
    }

    @Test
    fun getConnectedDevicesFailsThenReturnError() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesError)

        val result = syncRepo.getConnectedDevices()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenInitialPatchSucceedsThenReturnSuccess() = runTest {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncCrypter.generateAllData()).thenReturn(syncData)
        whenever(syncApi.sendAllBookmarks(token, syncData)).thenReturn(patchAllSuccess)

        val result = syncRepo.sendAllData()

        assertTrue(result is Result.Success)
    }

    @Test
    fun whenInitialPatchFailsThenReturnError() = runTest {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncCrypter.generateAllData()).thenReturn(syncData)
        whenever(syncApi.sendAllBookmarks(token, syncData)).thenReturn(patchAllError)

        val result = syncRepo.sendAllData()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenGenerateRecoveryCodeAsStringThenReturnExpectedJson() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)

        val result = syncRepo.getRecoveryCode()

        assertEquals(jsonRecoveryKeyEncoded, result)
    }

    @Test
    fun whenGenerateRecoveryCodeWithoutAccountThenReturnNull() {
        val result = syncRepo.getRecoveryCode()

        assertNull(result)
    }

    @Test
    fun whenGetConnectQRThenReturnExpectedJson() {
        whenever(nativeLib.prepareForConnect()).thenReturn(connectKeys)
        prepareToProvideDeviceIds()

        val result = syncRepo.getConnectQR() as Success

        assertEquals(jsonConnectKeyEncoded, result.data)
    }

    @Test
    fun whenConnectingUsingQRFromAuthenticatedDeviceThenConnectsDevice() {
        givenAuthenticatedDevice()
        whenever(nativeLib.seal(jsonRecoveryKey, primaryKey)).thenReturn(encryptedRecoveryCode)
        whenever(syncApi.connect(token, deviceId, encryptedRecoveryCode)).thenReturn(Result.Success(true))

        val result = syncRepo.connectDevice(jsonConnectKeyEncoded)

        verify(syncApi).connect(token, deviceId, encryptedRecoveryCode)
        assertTrue(result is Success)
    }

    @Test
    fun whenConnectingUsingQRFromUnauthenticatedDeviceThenAccountCreatedAndConnects() {
        whenever(syncStore.primaryKey).thenReturn(null).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        prepareToProvideDeviceIds()
        prepareForCreateAccountSuccess()
        whenever(nativeLib.seal(jsonRecoveryKey, primaryKey)).thenReturn(encryptedRecoveryCode)
        whenever(syncApi.connect(token, deviceId, encryptedRecoveryCode)).thenReturn(Result.Success(true))

        val result = syncRepo.connectDevice(jsonConnectKeyEncoded)

        verify(syncApi).connect(token, deviceId, encryptedRecoveryCode)
        assertTrue(result is Success)
    }

    @Test
    fun whenPollingConnectionKeysAndKeysFoundThenPerformLogin() {
        prepareForLoginSuccess()
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceSuccess)
        whenever(nativeLib.sealOpen(encryptedRecoveryCode, primaryKey, secretKey)).thenReturn(jsonRecoveryKey)

        val result = syncRepo.pollConnectionKeys()

        assertTrue(result is Success)
    }

    @Test
    fun whenPollingConnectionKeysAndKeysNotFoundThenError() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceKeysNotFoundError)

        val result = syncRepo.pollConnectionKeys()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenGetThisConnectedDeviceThenReturnExpectedDevice() {
        givenAuthenticatedDevice()
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.deviceName).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)

        val result = syncRepo.getThisConnectedDevice()

        assertEquals(deviceId, result.deviceId)
        assertEquals(deviceName, result.deviceName)
        assertEquals(deviceType, result.deviceType)
    }

    private fun prepareForLoginSuccess() {
        prepareForEncryption()
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginSuccess)
    }

    private fun givenAuthenticatedDevice() {
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.deviceName).thenReturn(deviceName)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.token).thenReturn(token)
    }

    private fun prepareToProvideDeviceIds() {
        whenever(syncDeviceIds.userId()).thenReturn(userId)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)
    }

    private fun prepareForCreateAccountSuccess() {
        prepareForEncryption()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Result.Success(AccountCreatedResponse(userId, token)))
    }

    private fun prepareForEncryption() {
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(decryptedSecretKey)
        whenever(nativeLib.decryptData(anyString(), primaryKey = eq(primaryKey))).thenAnswer {
            DecryptResult(0L, it.arguments.first() as String)
        }
        whenever(nativeLib.encryptData(anyString(), primaryKey = eq(primaryKey))).thenAnswer {
            EncryptResult(0L, it.arguments.first() as String)
        }
    }
}
