package com.droptracker;

import com.droptracker.PlayerDropClient.AddPlayerDropsRequestMessage;
import com.droptracker.PlayerDropClient.AddPlayerDropsResponseMessage;
import com.droptracker.PlayerDropClient.DropHttpMessage;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Singleton
public class MessageQueueHandler {

    private final ConcurrentHashMap<Long, LinkedBlockingQueue<DropHttpMessage>> _queuesByAccount = new ConcurrentHashMap<>();

    private static final int QUEUE_SIZE = 250;
    private static final int MESSAGE_BATCH_SIZE = 100;
    private static final String API_BASE_URL = "http://localhost:5117/";
    private static final String API_DROP_ENDPOINT = "api/drop";

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public MessageQueueHandler(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void queueMessage(DropHttpMessage dropMessage, long accountHash)
    {
        if (accountHash == -1)
        {
            log.debug("Cannot queue drops while logged out.");
            return;
        }

        var messageQueue = getQueueFor(accountHash);

        if (!messageQueue.offer(dropMessage))
        {
            log.debug("Message queue full ({}), dropping oldest unflushed drop", QUEUE_SIZE);
        }

    }

    public DropHttpMessage findDropByItemName(long accountHash, String itemName)
    {
        var queue = _queuesByAccount.get(accountHash);
        if (queue == null)
        {
            return null;
        }

        // Queue iterates head-to-tail (oldest first); keep the last match
        // so a repeated item name flips the most recent drop, not an older one.
        DropHttpMessage latest = null;
        for (DropHttpMessage msg : queue)
        {
            if (itemName.equals(msg.ItemName))
            {
                latest = msg;
            }
        }
        return latest;
    }

    public void flush(long accountHash)
    {
        try
        {
            if (accountHash == -1)
            {
                return;
            }

            var messageQueue = getQueueFor(accountHash);

            if (messageQueue.isEmpty())
            {
                return;
            }

            int messageQueueCount = messageQueue.size();
            int batchesToSend = (messageQueueCount + MESSAGE_BATCH_SIZE - 1) / MESSAGE_BATCH_SIZE; //Small match trick to round up, to always ensure we clear the queue

            for (int i = 0; i < batchesToSend; i++)
            {
                var dropBatch = new ConcurrentHashMap<String, DropHttpMessage>(100);
                DropHttpMessage msg;

                while (dropBatch.size() < MESSAGE_BATCH_SIZE && (msg = messageQueue.poll()) != null)
                {
                    dropBatch.put(msg.DropUUID, msg);
                }

                if (dropBatch.isEmpty()) { return; }

                sendDropBatch(dropBatch, accountHash, messageQueue);
            }
        }
        catch (Exception ex)
        {
            log.warn("Drop flush failed", ex);
        }
    }


    private LinkedBlockingQueue<DropHttpMessage> getQueueFor(long accountHash)
    {
        return _queuesByAccount.computeIfAbsent(accountHash,
                h -> new LinkedBlockingQueue<>(QUEUE_SIZE));
    }

    private void sendDropBatch(ConcurrentHashMap<String, DropHttpMessage> dropBatch, long accountHash, LinkedBlockingQueue<DropHttpMessage> messageQueue) {
        var apiRequest = new AddPlayerDropsRequestMessage();
        apiRequest.PlayerHash = accountHash;
        apiRequest.Drops = dropBatch.values();

        String json = gson.toJson(apiRequest);

        RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        Request request = new Request.Builder()
                .url(API_BASE_URL + API_DROP_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Drop batch upload failed, requeueing {} drops", dropBatch.size());
                dropBatch.forEach((uuid, msg) -> {
                    if (!messageQueue.offer(msg))
                    {
                        log.debug("Message queue full ({}), dropping requeued drop {}", QUEUE_SIZE, uuid);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        log.debug("Drop batch upload returned {}, requeueing {} drops",
                                response.code(), dropBatch.size());

                        dropBatch.forEach((uuid, msg) -> {
                            if (!messageQueue.offer(msg))
                            {
                                log.debug("Message queue full ({}), dropping requeued drop {}", QUEUE_SIZE, uuid);
                            }
                        });
                        return;
                    }

                    var apiResponse = gson.fromJson(response.body().string(),
                            AddPlayerDropsResponseMessage.class);

                    if (apiResponse.FailedDropUploads != null)
                    {
                        for (String uuid : apiResponse.FailedDropUploads)
                        {
                            DropHttpMessage msg = dropBatch.remove(uuid);
                            if (msg != null)
                            {
                                if (!messageQueue.offer(msg))
                                {
                                    log.debug("Message queue full ({}), dropping requeued drop {}", QUEUE_SIZE, uuid);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}
