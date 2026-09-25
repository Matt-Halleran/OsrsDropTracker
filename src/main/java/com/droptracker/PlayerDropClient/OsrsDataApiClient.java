package com.droptracker.PlayerDropClient;

import okhttp3.OkHttpClient;

public class OsrsDataApiClient {

    private OkHttpClient _okHttpClient;

    public OsrsDataApiClient(OkHttpClient okHttpClient)
    {
        _okHttpClient = okHttpClient;
    }

    public void SubmitPlayerDrop(DropHttpMessage message)
    {

    }
}
