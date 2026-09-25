package com.droptracker;

import com.droptracker.PlayerDropClient.OsrsDataApiClient;
import com.droptracker.PlayerDropClient.DropHttpMessage;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import okhttp3.OkHttpClient;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final OsrsDataApiClient _playerDropHttpClient;
    private final Client _client;

    private final static int VALUABLE_DROP_THRESHOLD = 1000000;
    private final static int QUEUE_SIZE = 250;
    private final static int MESSAGE_BATCH_SIZE = 100;

    private LinkedBlockingQueue<DropHttpMessage> _messageQueue;

    public DropEventHandler(ItemManager itemManager, OkHttpClient okHttpClient, Client client)
    {
        _itemManager = itemManager;
        _playerDropHttpClient = new OsrsDataApiClient(okHttpClient);
        _client = client;
        _messageQueue = new LinkedBlockingQueue<DropHttpMessage>(QUEUE_SIZE);
    }

    public void Flush()
    {
        var dropBatch = new ConcurrentHashMap<String, DropHttpMessage>(100);
        DropHttpMessage msg;

        while (dropBatch.size() < MESSAGE_BATCH_SIZE && (msg = _messageQueue.poll()) != null)
        {
            dropBatch.put(msg.DropUUID, msg);
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

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.IsCollectionLog)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueue.offer(dropMessage);

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

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.IsCollectionLog)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueue.offer(dropMessage);

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

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.IsCollectionLog)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueue.offer(dropMessage);

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

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.IsCollectionLog)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueue.offer(dropMessage);

            //if IsImportant call flush queue
        }
    }

}
