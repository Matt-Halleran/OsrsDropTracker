package com.droptracker;

import com.droptracker.PlayerDropClient.AddPlayerDropsRequestMessage;
import com.droptracker.PlayerDropClient.DropHttpMessage;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final Client _client;
    private final Gson _gson;

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
    }

    public void Flush()
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

            //if IsImportant call flush queue
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

            //if IsImportant call flush queue
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

            //if IsImportant call flush queue
        }
    }

}
