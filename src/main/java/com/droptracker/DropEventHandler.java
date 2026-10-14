package com.droptracker;

import com.droptracker.PlayerDropClient.DropHttpMessage;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class DropEventHandler
{
    private final ItemManager itemManager;
    private final Client client;
    private final MessageQueueHandler messageQueueHandler;
    private final Map<String, Long> collectionLogMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flushService;
    private final AtomicBoolean importantFlushScheduled = new AtomicBoolean(false);

    private static final int VALUABLE_DROP_THRESHOLD = 1000000;
    private static final long COLLECTION_LOG_LOOKBACK_MS = 10_000;


    @Inject
    public DropEventHandler(ItemManager itemManager, Client client, MessageQueueHandler messageQueueHandler, ScheduledExecutorService flushService)
    {
        this.itemManager = itemManager;
        this.client = client;
        this.messageQueueHandler = messageQueueHandler;
        this.flushService = flushService;
    }

    public void flush()
    {
        messageQueueHandler.flush(client.getAccountHash());
    }

    public void handleEventDrop(LootReceived lootReceived)
    {
        processLootReceived(lootReceived);
    }

    public void handleUnknownDrop(LootReceived lootReceived)
    {
        processLootReceived(lootReceived);
    }

    public void handleNpcDrop(ServerNpcLoot lootReceived)
    {
        processServerNpcLoot(lootReceived, LootRecordType.NPC);
    }

    public void handlePickpocketDrop(ServerNpcLoot lootReceived)
    {
        processServerNpcLoot(lootReceived, LootRecordType.PICKPOCKET);
    }

    public void handleCollectionLogEntry(String itemName)
    {
        long timeStamp = System.currentTimeMillis();
        long accountHash = client.getAccountHash();

        var dropMessage = messageQueueHandler.findDropByItemName(accountHash, itemName);

        if (dropMessage != null)
        {
            dropMessage.IsImportant = true;
            dropMessage.CollectionLogCompleted = true;

            messageQueueHandler.flush(accountHash);
            return;
        }

        collectionLogMessages.put(itemName, timeStamp);
    }

    private void processLootReceived(LootReceived lootReceived)
    {
        String timeStamp = Instant.now().toString();
        var items = lootReceived.getItems();
        long accountHash = client.getAccountHash();

        for (ItemStack item : items)
        {
            int itemId = item.getId();
            var itemComp = itemManager.getItemComposition(itemId);

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = itemId;
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.TimeStamp = timeStamp;
            dropMessage.GpValue = itemManager.getItemPrice(itemId);
            dropMessage.KillCount = -1;
            dropMessage.Source = lootReceived.getName();
            dropMessage.SourceType = lootReceived.getType().toString();

            if (isRecentCollectionLogEntry(dropMessage.ItemName))
            {
                dropMessage.CollectionLogCompleted = true;
            }

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            messageQueueHandler.queueMessage(dropMessage, accountHash);

            if (dropMessage.IsImportant)
            {
                scheduleImportantFlush();
            }
        }
    }

    private void processServerNpcLoot(ServerNpcLoot lootReceived, LootRecordType lootRecordType)
    {
        String timeStamp = Instant.now().toString();

        var items = lootReceived.getItems();
        var npcComp = lootReceived.getComposition();

        long accountHash = client.getAccountHash();

        for (ItemStack item : items)
        {
            int itemId = item.getId();
            var itemComp = itemManager.getItemComposition(itemId);

            var dropMessage = new DropHttpMessage();
            dropMessage.ItemId = itemId;
            dropMessage.ItemName = itemComp.getName();
            dropMessage.Quantity = item.getQuantity();
            dropMessage.GpValue = itemManager.getItemPrice(itemId);
            dropMessage.Source = npcComp.getName();
            dropMessage.KillCount = -1;
            dropMessage.TimeStamp = timeStamp;
            dropMessage.SourceType = lootRecordType.toString();
            dropMessage.SourceId = String.valueOf(npcComp.getId());

            if (isRecentCollectionLogEntry(dropMessage.ItemName))
            {
                dropMessage.CollectionLogCompleted = true;
            }

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            messageQueueHandler.queueMessage(dropMessage, accountHash);

            if (dropMessage.IsImportant)
            {
                scheduleImportantFlush();
            }
        }
    }

    private boolean isRecentCollectionLogEntry(String itemName)
    {
        Long loggedAt = collectionLogMessages.remove(itemName);
        return loggedAt != null && System.currentTimeMillis() - loggedAt <= COLLECTION_LOG_LOOKBACK_MS;
    }

    private void scheduleImportantFlush()
    {
        if (importantFlushScheduled.compareAndSet(false, true))
        {
            flushService.schedule(() ->
            {
                importantFlushScheduled.set(false);
                flush();
            }, 1200, TimeUnit.MILLISECONDS);
        }
    }

}
