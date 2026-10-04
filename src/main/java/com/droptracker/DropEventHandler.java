package com.droptracker;

import com.droptracker.PlayerDropClient.DropHttpMessage;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.*;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final Client _client;
    private final MessageQueueHandler _messageQueueHandler;
    private final Map<String, Long> _collectionLogMessages = new ConcurrentHashMap<String, Long>();
    private final ScheduledExecutorService _flushService;
    private final AtomicBoolean _importantFlushScheduled = new AtomicBoolean(false);

    private final static int VALUABLE_DROP_THRESHOLD = 1000000;
    private static final long COLLECTION_LOG_LOOKBACK_MS = 10_000;


    @Inject
    public DropEventHandler(ItemManager itemManager, Client client, MessageQueueHandler messageQueueHandler, ScheduledExecutorService flushService)
    {
        _itemManager = itemManager;
        _client = client;
        _messageQueueHandler = messageQueueHandler;
        _flushService = flushService;
    }

    public void Flush()
    {
        _messageQueueHandler.Flush(_client.getAccountHash());
    }

    public void HandleEventDrop(LootReceived lootReceived)
    {
        ProcessLootReceived(lootReceived);
    }

    public void HandleUnknownDrop(LootReceived lootReceived)
    {
        ProcessLootReceived(lootReceived);
    }

    public void HandleNpcDrop(ServerNpcLoot lootReceived)
    {
        ProcessServerNpcLoot(lootReceived, LootRecordType.NPC);
    }

    public void HandlePickpocketDrop(ServerNpcLoot lootReceived)
    {
        ProcessServerNpcLoot(lootReceived, LootRecordType.PICKPOCKET);
    }

    public void HandleCollectionLogEntry(String itemName)
    {
        long timeStamp = System.currentTimeMillis();
        long accountHash = _client.getAccountHash();

        var dropMessage = _messageQueueHandler.FindDropByItemName(accountHash, itemName);

        if (dropMessage != null)
        {
            dropMessage.IsImportant = true;
            dropMessage.CollectionLogCompleted = true;

            _messageQueueHandler.Flush(accountHash);
            return;
        }

        _collectionLogMessages.put(itemName, timeStamp);
    }

    private void ProcessLootReceived(LootReceived lootReceived)
    {
        String timeStamp = Instant.now().toString();
        var items = lootReceived.getItems();
        long accountHash = _client.getAccountHash();

        for (ItemStack item : items)
        {
            int itemId = item.getId();
            var itemComp = _itemManager.getItemComposition(itemId);

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = itemId;
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.TimeStamp = timeStamp;
            dropMessage.GpValue = _itemManager.getItemPrice(itemId);
            dropMessage.KillCount = -1;
            dropMessage.Source = lootReceived.getName();
            dropMessage.SourceType = lootReceived.getType().toString();

            if (IsRecentCollectionLogEntry(dropMessage.ItemName))
            {
                dropMessage.CollectionLogCompleted = true;
            }

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);

            if (dropMessage.IsImportant)
            {
                ScheduleImportantFlush();
            }
        }
    }

    private void ProcessServerNpcLoot(ServerNpcLoot lootReceived, LootRecordType lootRecordType)
    {
        String timeStamp = Instant.now().toString();

        var items = lootReceived.getItems();
        var npcComp = lootReceived.getComposition();

        long accountHash = _client.getAccountHash();

        for (ItemStack item : items)
        {
            int itemId = item.getId();
            var itemComp = _itemManager.getItemComposition(itemId);

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = itemId;
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = _itemManager.getItemPrice(itemId);
            dropMessage.Source = npcComp.getName();
            dropMessage.KillCount = -1;
            dropMessage.TimeStamp = timeStamp;
            dropMessage.SourceType = lootRecordType.toString();
            dropMessage.SourceId = String.valueOf(npcComp.getId());

            if (IsRecentCollectionLogEntry(dropMessage.ItemName))
            {
                dropMessage.CollectionLogCompleted = true;
            }

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);

            if (dropMessage.IsImportant)
            {
                ScheduleImportantFlush();
            }
        }
    }

    private boolean IsRecentCollectionLogEntry(String itemName)
    {
        Long loggedAt = _collectionLogMessages.remove(itemName);
        return loggedAt != null && System.currentTimeMillis() - loggedAt <= COLLECTION_LOG_LOOKBACK_MS;
    }

    private void ScheduleImportantFlush()
    {
        if (_importantFlushScheduled.compareAndSet(false, true))
        {
            _flushService.schedule(() ->
            {
                _importantFlushScheduled.set(false);
                Flush();
            }, 1200, TimeUnit.MILLISECONDS);
        }
    }

}
