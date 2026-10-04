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

@Slf4j
@Singleton
public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final Client _client;
    private final OkHttpClient _okHttpClient;
    private final MessageQueueHandler _messageQueueHandler;

    private final static int VALUABLE_DROP_THRESHOLD = 1000000;

    @Inject
    public DropEventHandler(ItemManager itemManager, OkHttpClient okHttpClient, Client client, MessageQueueHandler messageQueueHandler)
    {
        _itemManager = itemManager;
        _client = client;
        _okHttpClient = okHttpClient;
        _messageQueueHandler = messageQueueHandler;
    }

    public void Flush()
    {
        _messageQueueHandler.Flush(_client.getAccountHash());
    }

    public void HandleEventDrop(LootReceived lootReceived)
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
            dropMessage.GpValue = _itemManager.getItemPrice(itemId);
            dropMessage.Source = lootReceived.getName();
            dropMessage.KillCount = -1;
            dropMessage.TimeStamp = timeStamp;
            dropMessage.SourceType = lootReceived.getType().toString();

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);

            //if IsImportant call flush queue
        }
    }

    public void HandleNpcDrop(ServerNpcLoot lootReceived)
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
            dropMessage.SourceType = LootRecordType.NPC.toString();
            dropMessage.SourceId = String.valueOf(npcComp.getId());

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);
        }
    }

    public void HandlePickpocketDrop(ServerNpcLoot lootReceived)
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
            dropMessage.Source = npcComp.getName();
            dropMessage.SourceType = LootRecordType.PICKPOCKET.toString();
            dropMessage.SourceId = String.valueOf(npcComp.getId());

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);
        }
    }

    public void HandleUnknownDrop(LootReceived lootReceived)
    {
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
            dropMessage.GpValue = _itemManager.getItemPrice(itemId);
            dropMessage.Source = lootReceived.getName();
            dropMessage.KillCount = -1;
            dropMessage.SourceType = lootReceived.getType().toString();
            dropMessage.Source = lootReceived.getName();

            if (dropMessage.GpValue > VALUABLE_DROP_THRESHOLD || dropMessage.CollectionLogCompleted)
            {
                dropMessage.IsImportant = true;
            }

            _messageQueueHandler.QueueMessage(dropMessage, accountHash);
        }
    }
}
