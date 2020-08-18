package net.gleske.jervis.remotes.creds

import groovy.json.JsonBuilder
import java.io.IOException
import java.time.Instant
import net.gleske.jervis.remotes.SimpleRestServiceSupport
import net.gleske.jervis.remotes.interfaces.VaultRoleIdCredential

class VaultAppRoleCredential implements ReadonlyTokenCredential, SimpleRestServiceSupport {
    // values specific to token instance
    private final String vault_url
    private final VaultRoleIdCredential cred

    // values specific to token lifetime
    private Integer ttl = 0
    private String token
    private String token_type
    private Boolean renewable = false
    private Instant leaseCreated

    String baseUrl() {
        this.vault_url
    }

    Map header(Map headers = [:]) {
        if(this.token) {
            headers['X-Vault-Token'] = getToken()
        }
        headers
    }

    VaultAppRoleCredential(String vault_url, String role_id, String secret_id) {
        this(vault_url, new VaultRoleIdCredentialImpl(role_id, secret_id))
    }

    VaultAppRoleCredential(String vault_url, VaultRoleIdCredential cred) {
        this.vault_url = (vault_url[-1] == '/')? vault_url : vault_url + '/'
        this.cred = cred
    }

    private Boolean isExpired() {
        if(!token) {
            return true
        }
        Instant now = new Date().toInstant()
        if(ttl - (now.epochSecond - this.leaseCreated.epochSecond) > 15) {
            return false
        }
        true
    }

    private String mapToJson(Map m) {
        (m as JsonBuilder).toString()
    }

    void revokeToken() {
        if(!this.token) {
            return
        }
        if(this.token_type != 'batch') {
            apiFetch('v1/auth/token/revoke-self', [:], 'POST')
        }
        this.token = null
    }

    private void leaseToken() {
        if(!isExpired() || tryRenewToken()) {
            return
        }
        this.token = null
        String data = mapToJson([role_id: this.cred.role_id, secret_id: this.cred.secret_id])
        this.leaseCreated = new Date().toInstant()
        Map response = apiFetch('v1/auth/approle/login', [:], 'POST', data)
        this.ttl = response.auth.lease_duration
        this.renewable = response.auth.renewable
        this.token = response.auth.client_token
        this.token_type = response.auth.token_type
    }

    // (new Date().toInstant().epochSecond) - createdLease.epochSecond // gives seconds elapsed
    // new Date(((Integer) epoc_seconds as Long)*1000).toInstant() // gets instant from given epoch timestamp
    String getToken() {
        leaseToken()
        this.token
    }

    Boolean tryRenewToken() {
        if(!this.token || !this.renewable) {
            return false
        }
        try {
            String data = mapToJson([increment: "${this.ttl}s"])
            Map response = apiFetch('v1/auth/token/renew-self', [:], 'POST', data)
            this.leaseCreated = new Date().toInstant()
            this.ttl = response.auth.lease_duration
            this.renewable = response.auth.renewable
            this.token = response.auth.client_token
            this.token_type = response.auth.token_type
            return true
        } catch(IOException e) {
            return false
        }
    }

    Map lookupSelfToken() throws IOException {
        apiFetch('v1/auth/token/lookup-self')
    }
}
