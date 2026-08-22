import io

p = 'app/src/main/java/com/wjy/foxchat/data/remote/SupabaseRemote.kt'
s = io.open(p, encoding='utf-8').read()

old = '''        JSONObject(raw).optString("access_token").also {
            require(it.isNotBlank()) { "匿名会话创建失败" }
            accessToken = it
        }
    }'''

new = '''        val json = JSONObject(raw)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        require(access.isNotBlank()) { "匿名会话创建失败" }
        accessToken = access
        AuthSession(access, refresh)
    }

    suspend fun refreshAccessToken(refreshToken: String): Result<AuthSession> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONObject().apply { put("refresh_token", refreshToken) }
        val raw = execute(
            Request.Builder()
                .url("${baseUrl()}/auth/v1/token?grant_type=refresh_token")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
        val json = JSONObject(raw)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token").ifBlank { refreshToken }
        require(access.isNotBlank()) { "刷新会话失败" }
        accessToken = access
        AuthSession(access, refresh)
    }'''

if old not in s:
    print('OLD NOT FOUND')
else:
    s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('REPLACED OK')
