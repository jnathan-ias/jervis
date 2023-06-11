/*
   Copyright 2014-2023 Sam Gleske - https://github.com/samrocketman/jervis

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   */
package net.gleske.jervis.remotes.creds
//the EphemeralTokenCacheTest() class automatically sees the EphemeralTokenCache() class because they're in the same package

import net.gleske.jervis.exceptions.TokenException

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.junit.After
import org.junit.Before
import org.junit.Test

class EphemeralTokenCacheTest extends GroovyTestCase {

    EphemeralTokenCache tokenCache

    //set up before every test
    @Before protected void setUp() {
        super.setUp()
        this.tokenCache = new EphemeralTokenCache({-> ''})
        tokenCache.loadCache = null
        tokenCache.saveCache = null
        tokenCache.obtainLock = null
    }
    //tear down after every test
    @After protected void tearDown() {
        this.tokenCache = null
        super.tearDown()
    }
    @Test public void test_EphemeralTokenCache_fail_instantiation() {
        shouldFail(IllegalStateException) {
            new EphemeralTokenCache()
        }
    }
    @Test public void test_EphemeralTokenCache_isExpired_without_existing() {
        String hash = 'fake'
        assert this.tokenCache.isExpired(hash) == true
    }
    @Test public void test_EphemeralTokenCache_isExpired_with_existing() {
        String hash = 'fake'
        String tenSecondsFromNow = Instant.now().plus(10, ChronoUnit.SECONDS).toString()

        // without 30 second renew buffer
        this.tokenCache.renew_buffer = 0
        this.tokenCache.updateTokenWith('sometoken', tenSecondsFromNow, hash)
        assert this.tokenCache.isExpired(hash) == false

        // with 30 second renew buffer
        this.tokenCache.renew_buffer = 30
        assert this.tokenCache.isExpired(hash) == true
    }
    @Test public void test_EphemeralTokenCache_updateTokenWith_protect_renew_buffer_misconfiguration() {
        String hash = 'fake'
        String tenSecondsFromNow = Instant.now().plus(10, ChronoUnit.SECONDS).toString()
        shouldFail(TokenException) {
            this.tokenCache.updateTokenWith('sometoken', tenSecondsFromNow, hash)
        }
        this.tokenCache.renew_buffer = 0
        this.tokenCache.updateTokenWith('sometoken', tenSecondsFromNow, hash)
        assert this.tokenCache.token == 'sometoken'
    }
    @Test public void test_EphemeralTokenCache_updateTokenWith_mixed_cache_with_renew_buffers_and_cleanup() {
        String tenSecondsFromNow = Instant.now().plus(10, ChronoUnit.SECONDS).toString()
        String thirtyFiveSecondsFromNow = Instant.now().plus(35, ChronoUnit.SECONDS).toString()

        this.tokenCache.renew_buffer = 0
        this.tokenCache.updateTokenWith('sometoken', tenSecondsFromNow, '10sHash')
        this.tokenCache.renew_buffer = 30
        this.tokenCache.updateTokenWith('sometoken2', thirtyFiveSecondsFromNow, '30sHash')

        assert this.tokenCache.cache.keySet().toList() == ['10sHash', '30sHash']
        assert this.tokenCache.cache['10sHash'].renew_buffer == 0
        assert this.tokenCache.cache['30sHash'].renew_buffer == 30
        assert this.tokenCache.token == 'sometoken2'
        // check automated cleanup of expired tokens
        this.tokenCache.cache['10sHash'].renew_buffer = 30
        this.tokenCache.updateTokenWith('sometoken3', thirtyFiveSecondsFromNow, '30sHash')
        assert this.tokenCache.cache.keySet().toList() == ['30sHash']
        assert this.tokenCache.cache['30sHash'].renew_buffer == 30
        assert this.tokenCache.token == 'sometoken3'
    }
}
