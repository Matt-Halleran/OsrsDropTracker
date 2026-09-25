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
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

public class DropEventHandler
{
    private final ItemManager _itemManager;
    private final OsrsDataApiClient _playerDropHttpClient;
    private final Client _client;
    private final static int VALUABLE_DROP_THRESHOLD = 1000000;

    private ConcurrentHashMap<String, DropHttpMessage> _messageQueue;

    public DropEventHandler(ItemManager itemManager, OkHttpClient okHttpClient, Client client)
    {
        _itemManager = itemManager;
        _playerDropHttpClient = new OsrsDataApiClient(okHttpClient);
        _client = client;
        _messageQueue = new ConcurrentHashMap<String, DropHttpMessage>(250);
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

            _messageQueue.put(dropMessage.DropUUID, dropMessage);

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

            _messageQueue.put(dropMessage.DropUUID, dropMessage);

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

            _messageQueue.put(dropMessage.DropUUID, dropMessage);

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

            _messageQueue.put(dropMessage.DropUUID, dropMessage);

            //if IsImportant call flush queue
        }
    }

    private LinkedList<DropHttpMessage> GetHttpDrops(Collection<ItemStack> items)
    {
        var drops = new LinkedList<DropHttpMessage>();

        for (ItemStack item : items)
        {
            var drop = new DropHttpMessage();
            int itemId = item.getId();
            var itemComposition = _itemManager.getItemComposition(itemId);

            drop.ItemId = itemId;
            drop.ItemName = itemComposition.getName();
            drop.GpValue = itemComposition.getPrice();
            drop.Quantity = item.getQuantity();

            drops.add(drop);
        }

        return drops;
    }

}
