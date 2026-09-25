package com.droptracker;

import com.droptracker.PlayerDropClient.AddPlayerDropsRequestMessage;
import com.droptracker.PlayerDropClient.AddPlayerDropsResponseMessage;
import com.droptracker.PlayerDropClient.DropHttpMessage;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import okhttp3.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final Client _client;
    private final Gson _gson;
    private final OkHttpClient _okHttpClient;

    private final static int VALUABLE_DROP_THRESHOLD = 1000000;
    private final static int QUEUE_SIZE = 250;
    private final static int MESSAGE_BATCH_SIZE = 100;
    private final static String API_BASE_URL = "http://localhost:5117/";
    private final static String API_DROP_ENDPOINT = "api/drop";

    private LinkedBlockingQueue<DropHttpMessage> _messageQueue;

    public DropEventHandler(ItemManager itemManager, OkHttpClient okHttpClient, Client client, Gson gson)
    {
        _itemManager = itemManager;
        _client = client;
        _messageQueue = new LinkedBlockingQueue<DropHttpMessage>(QUEUE_SIZE);
        _gson = gson;
        _okHttpClient = okHttpClient;
    }

    public void Flush()
    {
        try
        {
            if (_messageQueue.isEmpty())
            {
                return;
            }

            long accountHash = _client.getAccountHash();
            if (accountHash == -1)
            {
                return;
            }

            int messageQueueCount = _messageQueue.size();
            int batchesToSend = (messageQueueCount + MESSAGE_BATCH_SIZE - 1) / MESSAGE_BATCH_SIZE; //Small match trick to round up, to always ensure we clear the queue

            for (int i = 0; i < batchesToSend; i++)
            {
                var dropBatch = new ConcurrentHashMap<String, DropHttpMessage>(100);
                DropHttpMessage msg;

                while (dropBatch.size() < MESSAGE_BATCH_SIZE && (msg = _messageQueue.poll()) != null)
                {
                    dropBatch.put(msg.DropUUID, msg);
                }

                if (dropBatch.isEmpty()) { return; }

                SendDropBatch(dropBatch, String.valueOf(accountHash));
            }
        }
        catch (Exception ex)
        {
            log.warn("Drop flush failed", ex);
        }

    }

    public void HandleEventDrop(LootReceived lootReceived)
    {
        var items = lootReceived.getItems();
        String timeStamp = Instant.now().toString();

        for (ItemStack item : items)
        {
            var itemComp = _itemManager.getItemComposition(item.getId());

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = item.getId();
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = itemComp.getPrice();
            dropMessage.Source = lootReceived.getName();
            dropMessage.KillCount = -1;
            dropMessage.TimeStamp = timeStamp;

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            if (!_messageQueue.offer(dropMessage))
            {
                log.debug("Message queue full ({}), dropping oldest unflushed drop", QUEUE_SIZE);
            }

            //if IsImportant call flush queue
        }
    }

    public void HandleNpcDrop(ServerNpcLoot lootReceived)
    {
        var items = lootReceived.getItems();
        var npcComp = lootReceived.getComposition();
        String timeStamp = Instant.now().toString();

        for (ItemStack item : items)
        {
            var itemComp = _itemManager.getItemComposition(item.getId());

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = item.getId();
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = itemComp.getPrice();
            dropMessage.Source = npcComp.getName();
            dropMessage.KillCount = -1;
            dropMessage.TimeStamp = timeStamp;

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            if (!_messageQueue.offer(dropMessage))
            {
                log.debug("Message queue full ({}), dropping oldest unflushed drop", QUEUE_SIZE);
            }
        }
    }

    public void HandlePickpocketDrop(ServerNpcLoot lootReceived)
    {
        var items = lootReceived.getItems();
        var npcComp = lootReceived.getComposition();

        for (ItemStack item : items)
        {
            var itemComp = _itemManager.getItemComposition(item.getId());

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = item.getId();
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = itemComp.getPrice();
            dropMessage.Source = npcComp.getName();
            dropMessage.KillCount = -1;

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            if (!_messageQueue.offer(dropMessage))
            {
                log.debug("Message queue full ({}), dropping oldest unflushed drop", QUEUE_SIZE);
            }
        }
    }

    public void HandleUnknownDrop(LootReceived lootReceived)
    {
        var items = lootReceived.getItems();

        for (ItemStack item : items)
        {
            var itemComp = _itemManager.getItemComposition(item.getId());

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = item.getId();
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = itemComp.getPrice();
            dropMessage.Source = lootReceived.getName();
            dropMessage.KillCount = -1;

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            if (!_messageQueue.offer(dropMessage))
            {
                log.debug("Message queue full ({}), dropping oldest unflushed drop", QUEUE_SIZE);
            }
        }
    }

    private void SendDropBatch(ConcurrentHashMap<String, DropHttpMessage> dropBatch, String accountHash) {
        var apiRequest = new AddPlayerDropsRequestMessage();
        apiRequest.PlayerHash = accountHash;
        apiRequest.Drops = dropBatch.values();

        String json = _gson.toJson(apiRequest);

        RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        Request request = new Request.Builder()
                .url(API_BASE_URL + API_DROP_ENDPOINT)
                .post(body)
                .build();

        _okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Drop batch upload failed, requeueing {} drops", dropBatch.size());
                dropBatch.forEach((uuid, msg) -> _messageQueue.offer(msg));
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
                        dropBatch.forEach((uuid, msg) -> _messageQueue.offer(msg));
                        return;
                    }

                    var apiResponse = _gson.fromJson(response.body().string(),
                            AddPlayerDropsResponseMessage.class);

                    if (apiResponse.FailedDropUploads != null)
                    {
                        for (String uuid : apiResponse.FailedDropUploads)
                        {
                            DropHttpMessage msg = dropBatch.remove(uuid);
                            if (msg != null)
                            {
                                _messageQueue.offer(msg);
                            }
                        }
                    }
                }
            }
        });
    }
}
