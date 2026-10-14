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
            dropMessage.isImportant = true;
            dropMessage.collectionLogCompleted = true;

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
            dropMessage.itemId = itemId;
            dropMessage.itemName = itemComp.getName();
            dropMessage.quantity = item.getQuantity();
            dropMessage.timeStamp = timeStamp;
            dropMessage.gpValue = itemManager.getItemPrice(itemId);
            dropMessage.killCount = -1;
            dropMessage.source = lootReceived.getName();
            dropMessage.sourceType = lootReceived.getType().toString();

            if (isRecentCollectionLogEntry(dropMessage.itemName))
            {
                dropMessage.collectionLogCompleted = true;
            }

            if (dropMessage.gpValue > VALUABLE_DROP_THRESHOLD || dropMessage.collectionLogCompleted)
            {
                dropMessage.isImportant = true;
            }

            messageQueueHandler.queueMessage(dropMessage, accountHash);

            if (dropMessage.isImportant)
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
            dropMessage.itemId = itemId;
            dropMessage.itemName = itemComp.getName();
            dropMessage.quantity = item.getQuantity();
            dropMessage.gpValue = itemManager.getItemPrice(itemId);
            dropMessage.source = npcComp.getName();
            dropMessage.killCount = -1;
            dropMessage.timeStamp = timeStamp;
            dropMessage.sourceType = lootRecordType.toString();
            dropMessage.sourceId = String.valueOf(npcComp.getId());

            if (isRecentCollectionLogEntry(dropMessage.itemName))
            {
                dropMessage.collectionLogCompleted = true;
            }

            if (dropMessage.gpValue > VALUABLE_DROP_THRESHOLD || dropMessage.collectionLogCompleted)
            {
                dropMessage.isImportant = true;
            }

            messageQueueHandler.queueMessage(dropMessage, accountHash);

            if (dropMessage.isImportant)
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
